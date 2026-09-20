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
 * Real BOOST engine for non-gaming apps (Telegram, YouTube, Instagram, ...).
 *
 * IMPORTANT SCOPE NOTE (see final report): this app is a single-outbound VPN client -- every app
 * on the phone shares the same active proxy connection. Routing different selected apps through
 * genuinely different outbound servers at the same time would require per-app multi-outbound
 * routing rules added to the core config generator, which is a much larger change than this task
 * covers. So BOOST here is real but connection-level: while enabled, it continuously measures the
 * real ping/jitter/packet-loss of the connection the selected apps are using, and auto-switches
 * the whole connection to a measurably better server in the group when it degrades -- which is
 * exactly what benefits Telegram/YouTube/etc. when their speed drops, just not app-by-app.
 * Per-app-different-route BOOST is marked NOT SUPPORTED in the final report for that reason.
 */
object BoostEngine {

    private val _diagnostics = MutableStateFlow(OptimizeDiagnostics(mode = "BOOST", label = "N/A"))
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
    private const val SAMPLES = 4
    private const val CANDIDATE_SAMPLES = 3
    private const val DEGRADED_SCORE_THRESHOLD = 500.0
    private const val REQUIRED_BAD_STREAK = 2
    private const val MIN_IMPROVEMENT_SCORE = 50.0

    fun start(context: Context, groupId: String) {
        stop()
        val appContext = context.applicationContext
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                if (isEnabled()) {
                    try {
                        tick(appContext, groupId)
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "BoostEngine tick failed", e)
                    }
                } else {
                    _diagnostics.value = OptimizeDiagnostics(mode = "BOOST", label = "N/A", status = "INACTIVE")
                }
                delay(CHECK_INTERVAL_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        // pendingRollback intentionally survives stop() -- see GamingEngine for why.
    }

    fun isEnabled(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_BOOST_ENABLED, false)

    private suspend fun tick(context: Context, groupId: String) {
        val currentGuid = MmkvManager.getSelectServer()
        val selectedApps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_BOOST_APPS_SET)?.toList() ?: emptyList()
        if (currentGuid.isNullOrEmpty() || selectedApps.isEmpty()) {
            _diagnostics.value = OptimizeDiagnostics(mode = "BOOST", label = "N/A", status = "INACTIVE")
            return
        }
        val config = MmkvManager.decodeServerConfig(currentGuid)
        val allGuids = MmkvManager.decodeServerList(groupId)
        val label = selectedApps.joinToString(", ")

        val metrics = StabilityMeter.measure(context, currentGuid, SAMPLES)
        publish(currentGuid, config, metrics, label)

        pendingRollback?.let { pending ->
            pendingRollback = null
            val stillFresh = System.currentTimeMillis() - pending.timestampMillis < 10 * 60 * 1000L
            if (currentGuid == pending.switchedToGuid && stillFresh) {
                badStreak = 0
                if (metrics.isMeasurable && metrics.stabilityScore < pending.previousScore) {
                    LogUtil.i(AppConfig.TAG, "BoostEngine: new route measured worse, rolling back")
                    switchTo(pending.previousGuid)
                }
                return
            }
        }

        badStreak = if (metrics.isMeasurable && metrics.stabilityScore < DEGRADED_SCORE_THRESHOLD) badStreak + 1 else 0
        if (badStreak < REQUIRED_BAD_STREAK || allGuids.size <= 1) return

        var bestGuid: String? = null
        var bestScore = -1.0
        for (guid in allGuids) {
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

    private fun publish(
        guid: String,
        config: com.v2ray.ang.dto.entities.ProfileItem?,
        metrics: RouteMetrics,
        label: String
    ) {
        _diagnostics.value = OptimizeDiagnostics(
            mode = "BOOST",
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
            routeLock = false,
            lastCheckMillis = System.currentTimeMillis()
        )
    }
}
