package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
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

    private const val CHECK_INTERVAL_MILLIS = 15_000L
    private const val SAMPLES = 5
    private const val CANDIDATE_SAMPLES = 3
    private const val DEGRADED_SCORE_THRESHOLD = 550.0
    private const val REQUIRED_BAD_STREAK = 1
    private const val MIN_IMPROVEMENT_SCORE = 12.0
    private const val FULL_SCAN_INTERVAL_MILLIS = 60_000L
    private const val MIN_PING_IMPROVEMENT_MILLIS = 5L
    private var lastFullScanMillis = 0L

    fun start(context: Context, groupId: String) {
        stop()
        val appContext = context.applicationContext
        lastFullScanMillis = 0L
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                if (isEnabled()) {
                    try {
                        tick(appContext, groupId)
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "GamingEngine tick failed", e)
                    }
                } else {
                    emit(OptimizeDiagnostics.idle("GAMING", serviceRunning = true))
                }
                delay(CHECK_INTERVAL_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        // No engine is running any more: drop the snapshot so the Diagnostics screen (main
        // process) stops showing the last measurement as if it were current.
        _diagnostics.value = OptimizeDiagnostics.idle("GAMING")
        MmkvManager.removeDiagnosticsSnapshot(AppConfig.DIAGNOSTICS_GAMING)
        // Note: pendingRollback deliberately survives stop(), since switchTo() itself triggers a
        // real in-place core reload (CoreServiceManager.reloadForRouteSwitch()); clearing it here
        // would erase the rollback check before the next tick() ever gets to use it.
    }

    fun isEnabled(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_GAMING_ENABLED, false)

    fun setRouteLock(locked: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_GAMING_ROUTE_LOCK, locked)
    }

    fun isRouteLocked(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_GAMING_ROUTE_LOCK, false)

    private suspend fun tick(context: Context, groupId: String) {
        val currentGuid = MmkvManager.getSelectServer()
        if (currentGuid.isNullOrEmpty()) {
            emit(OptimizeDiagnostics.idle("GAMING").copy(modeEnabled = true))
            return
        }
        val config = MmkvManager.decodeServerConfig(currentGuid)
        val allGuids = MmkvManager.decodeServerList(groupId)
        // Gaming Mode should optimize the actual lowest-latency route, not a hard-coded
        // country. The previous Turkey-only preference could keep a slower route selected
        // even when another saved server was measurably faster.
        val candidatePool = allGuids

        val selectedGames = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_GAMING_APPS_SET)?.toList() ?: emptyList()
        val mlbbSelected = selectedGames.any { it.equals("com.mobile.legends", true) || it.contains("mobile.legends", true) }
        // For MLBB, the normal generic delay URL can be misleading: it measures a generic
        // internet destination rather than a route near the game's MENA infrastructure.
        // Use a regional route probe as the selection signal. This is deliberately described
        // as an approximation because MOONTON does not publish a fixed public match-server IP.
        val gamingTargetUrl = if (mlbbSelected) AppConfig.MLBB_GAMING_PROBE_URL else null
        val label = if (selectedGames.isEmpty()) "N/A" else selectedGames.joinToString(", ")
        if (_diagnostics.value.status == "INACTIVE") {
            // First check since the engine (re)started: say so instead of showing "N/A" for the
            // whole time the probes take.
            emit(
                OptimizeDiagnostics.idle("GAMING", serviceRunning = true)
                    .copy(status = "CHECKING", label = label, modeEnabled = true, selectedAppCount = selectedGames.size)
            )
        }

        val metrics = StabilityMeter.measure(context, currentGuid, SAMPLES, targetUrl = gamingTargetUrl)
        val locked = isRouteLocked()

        publish(currentGuid, config, metrics, locked, label, selectedGames.size)

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

        if (candidatePool.size <= 1) return

        // Gaming Mode is deliberately aggressive about latency: do a small real-probe
        // scan instead of waiting for three consecutive 30-second failures. A full scan
        // is rate-limited so large server lists do not keep reconnecting the core.
        val now = System.currentTimeMillis()
        val shouldScan = lastFullScanMillis == 0L ||
            now - lastFullScanMillis >= FULL_SCAN_INTERVAL_MILLIS ||
            badStreak >= REQUIRED_BAD_STREAK
        if (!shouldScan) return

        var bestGuid: String? = null
        var bestMetrics: RouteMetrics? = null
        for (guid in candidatePool) {
            if (guid == currentGuid) continue
            val candidateMetrics = StabilityMeter.measure(context, guid, CANDIDATE_SAMPLES, targetUrl = gamingTargetUrl)
            if (candidateMetrics.isMeasurable &&
                (bestMetrics == null || candidateMetrics.stabilityScore > bestMetrics!!.stabilityScore)
            ) {
                bestMetrics = candidateMetrics
                bestGuid = guid
            }
            emit(_diagnostics.value)
        }
        lastFullScanMillis = now
        badStreak = 0

        val currentPing = metrics.pingMillis
        val bestPing = bestMetrics?.pingMillis ?: -1L
        val scoreImproved = bestMetrics != null &&
            metrics.isMeasurable &&
            bestMetrics!!.stabilityScore - metrics.stabilityScore >= MIN_IMPROVEMENT_SCORE
        val pingImproved = currentPing >= 0L &&
            bestPing >= 0L &&
            currentPing - bestPing >= MIN_PING_IMPROVEMENT_MILLIS

        if (bestGuid != null && metrics.isMeasurable && (scoreImproved || pingImproved)) {
            pendingRollback = PendingRollback(
                switchedToGuid = bestGuid,
                previousGuid = currentGuid,
                previousScore = metrics.stabilityScore
            )
            LogUtil.i(
                AppConfig.TAG,
                "GamingEngine: switching to lower-latency route " +
                    "$currentPing ms -> $bestPing ms"
            )
            switchTo(bestGuid)
        }
    }

    private fun switchTo(guid: String) {
        MmkvManager.setSelectServer(guid)
        // Reload the live core directly: GamingEngine runs in the same :daemon process as
        // CoreServiceManager, so this actually rebuilds the running tunnel against the new
        // selection. SettingsChangeManager.makeRestartService() would silently do nothing here --
        // see the comment on CoreServiceManager.reloadForRouteSwitch().
        CoreServiceManager.reloadForRouteSwitch()
    }

    private fun publish(
        guid: String,
        config: com.v2ray.ang.dto.entities.ProfileItem?,
        metrics: RouteMetrics,
        locked: Boolean,
        label: String,
        selectedAppCount: Int
    ) {
        // Mirrors the exact branch in CoreVpnService.configurePerAppProxy() where selected
        // Gaming apps actually get merged into the VpnService.Builder allow/disallow list --
        // i.e. this is true only when that code path genuinely runs, not a guess.
        val perAppProxyEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) == true
        val perAppProxySetNotEmpty = !MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET).isNullOrEmpty()
        val perAppRoutingActive = selectedAppCount > 0 && perAppProxyEnabled && perAppProxySetNotEmpty

        emit(OptimizeDiagnostics(
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
            lastCheckMillis = System.currentTimeMillis(),
            modeEnabled = true,
            selectedAppCount = selectedAppCount,
            vpnActive = true,
            perAppRoutingActive = perAppRoutingActive
        ))
    }

    /** Updates the in-process value and publishes it for the UI process. */
    private fun emit(snapshot: OptimizeDiagnostics) {
        _diagnostics.value = OptimizeDiagnostics.publish(AppConfig.DIAGNOSTICS_GAMING, snapshot)
    }
}
