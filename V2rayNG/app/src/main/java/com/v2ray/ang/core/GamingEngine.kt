package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real Gaming stability engine.
 *
 * There is no structured "country" field on a server (this app only stores free-text remarks),
 * so the "Turkey Gaming Route" the project currently relies on is identified by matching each
 * server's remarks against common Turkey markers (name, flag emoji, ISO code as a whole word).
 * If the user's saved servers don't contain a match, the engine honestly falls back to
 * monitoring/optimizing across the whole active group instead of silently pretending a Turkey
 * route exists.
 *
 * Every metric comes from [StabilityMeter], i.e. real network probes. The engine never switches
 * on a single bad sample (temporary spikes are ignored); it requires [REQUIRED_BAD_STREAK]
 * consecutive degraded checks, a candidate that is measurably better by at least
 * [MIN_IMPROVEMENT_SCORE], and it re-checks one cycle after switching -- rolling back if the new
 * route actually measured worse.
 */
object GamingEngine {

    private val _diagnostics = MutableStateFlow(OptimizeDiagnostics(mode = "GAMING", label = "N/A"))
    val diagnostics: StateFlow<OptimizeDiagnostics> = _diagnostics.asStateFlow()

    private var job: Job? = null
    private var badStreak = 0
    private var pendingRollback: PendingRollback? = null

    private data class PendingRollback(
        val switchedToGuid: String,
        val previousGuid: String,
        val previousScore: Double,
        val timestampMillis: Long = System.currentTimeMillis()
    )

    private const val CHECK_INTERVAL_MILLIS = 30_000L
    private const val SAMPLES = 5
    private const val CANDIDATE_SAMPLES = 3
    private const val DEGRADED_SCORE_THRESHOLD = 550.0
    private const val REQUIRED_BAD_STREAK = 3
    private const val MIN_IMPROVEMENT_SCORE = 60.0

    fun start(context: Context, groupId: String) {
        stop()
        val appContext = context.applicationContext
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                if (isEnabled()) {
                    try {
                        tick(appContext, groupId)
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "GamingEngine tick failed", e)
                    }
                } else {
                    _diagnostics.value = OptimizeDiagnostics(mode = "GAMING", label = "N/A", status = "INACTIVE")
                }
                delay(CHECK_INTERVAL_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        // Note: pendingRollback deliberately survives stop(), since switchTo() itself triggers a
        // core restart (stop -> start) via SettingsChangeManager.makeRestartService(); clearing it
        // here would erase the rollback check before the next tick() ever gets to use it.
    }

    fun isEnabled(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_GAMING_ENABLED, false)

    fun setRouteLock(locked: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_GAMING_ROUTE_LOCK, locked)
    }

    fun isRouteLocked(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_GAMING_ROUTE_LOCK, false)

    private suspend fun tick(context: Context, groupId: String) {
        val currentGuid = MmkvManager.getSelectServer()
        if (currentGuid.isNullOrEmpty()) {
            _diagnostics.value = OptimizeDiagnostics(mode = "GAMING", label = "N/A", status = "INACTIVE")
            return
        }
        val config = MmkvManager.decodeServerConfig(currentGuid)
        val allGuids = MmkvManager.decodeServerList(groupId)
        val turkeyGuids = allGuids.filter { isTurkeyRoute(it) }
        val candidatePool = turkeyGuids.ifEmpty { allGuids }
        val usingTurkeyPool = turkeyGuids.isNotEmpty()

        val metrics = StabilityMeter.measure(context, currentGuid, SAMPLES)
        val locked = isRouteLocked()
        val selectedGames = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_GAMING_APPS_SET)?.toList() ?: emptyList()
        val label = if (selectedGames.isEmpty()) "N/A" else selectedGames.joinToString(", ")

        publish(currentGuid, config, metrics, locked, label)

        pendingRollback?.let { pending ->
            pendingRollback = null
            val stillFresh = System.currentTimeMillis() - pending.timestampMillis < 10 * 60 * 1000L
            if (currentGuid == pending.switchedToGuid && stillFresh) {
                badStreak = 0
                if (metrics.isMeasurable && metrics.stabilityScore < pending.previousScore) {
                    LogUtil.i(AppConfig.TAG, "GamingEngine: new route measured worse, rolling back")
                    switchTo(pending.previousGuid)
                }
                return
            }
            // Guid changed since the switch (user picked something else manually) or the
            // rollback window expired: the stale pending check is simply discarded.
        }

        if (locked) {
            badStreak = 0
            return
        }

        badStreak = if (metrics.isMeasurable && metrics.stabilityScore < DEGRADED_SCORE_THRESHOLD) badStreak + 1 else 0

        if (badStreak < REQUIRED_BAD_STREAK || candidatePool.size <= 1) return
        if (!usingTurkeyPool) {
            // No known Turkey route among the saved servers: still allow switching within the
            // full group (better than doing nothing), but this is the documented fallback.
            LogUtil.i(AppConfig.TAG, "GamingEngine: no Turkey-tagged route found, evaluating full group instead")
        }

        var bestGuid: String? = null
        var bestScore = -1.0
        for (guid in candidatePool) {
            if (guid == currentGuid) continue
            val candidateMetrics = StabilityMeter.measure(context, guid, CANDIDATE_SAMPLES)
            if (candidateMetrics.isMeasurable && candidateMetrics.stabilityScore > bestScore) {
                bestScore = candidateMetrics.stabilityScore
                bestGuid = guid
            }
        }
        badStreak = 0
        if (bestGuid != null && metrics.isMeasurable && bestScore - metrics.stabilityScore >= MIN_IMPROVEMENT_SCORE) {
            pendingRollback = PendingRollback(switchedToGuid = bestGuid, previousGuid = currentGuid, previousScore = metrics.stabilityScore)
            switchTo(bestGuid)
        }
    }

    private fun switchTo(guid: String) {
        MmkvManager.setSelectServer(guid)
        SettingsChangeManager.makeRestartService()
    }

    private fun isTurkeyRoute(guid: String): Boolean {
        val remarks = MmkvManager.decodeServerConfig(guid)?.remarks ?: return false
        if (remarks.contains("ترکیه") || remarks.contains("🇹🇷")) return true
        val lower = remarks.lowercase()
        if (lower.contains("turkey") || lower.contains("türkiye") || lower.contains("turkiye")) return true
        return Regex("(?<![a-z])tr(?![a-z])", RegexOption.IGNORE_CASE).containsMatchIn(remarks)
    }

    private fun publish(
        guid: String,
        config: com.v2ray.ang.dto.entities.ProfileItem?,
        metrics: RouteMetrics,
        locked: Boolean,
        label: String
    ) {
        _diagnostics.value = OptimizeDiagnostics(
            mode = "GAMING",
            label = label,
            routeGuid = guid,
            routeName = config?.remarks,
            server = config?.server,
            transport = config?.network,
            dns = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS),
            pingMillis = metrics.pingMillis.takeIf { it >= 0 },
            jitterMillis = metrics.jitterMillis.takeIf { it >= 0 },
            lossPercent = metrics.lossPercent.takeIf { it >= 0 },
            stabilityScore = metrics.stabilityScore.takeIf { it >= 0 },
            status = if (metrics.isMeasurable && metrics.stabilityScore >= DEGRADED_SCORE_THRESHOLD) "ACTIVE" else "DEGRADED",
            routeLock = locked,
            lastCheckMillis = System.currentTimeMillis()
        )
    }
}
