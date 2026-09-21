package com.v2ray.ang.core

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil

/** Real-time, real-measurement-only snapshot shown by the Diagnostics panel. Any field that
 * can't be genuinely measured on this device/network is left null and the UI must render it as
 * "N/A" rather than guessing a value. */
data class OptimizeDiagnostics(
    val mode: String,
    val label: String,
    val routeGuid: String? = null,
    val routeName: String? = null,
    val server: String? = null,
    val transport: String? = null,
    val network: String = "IPv4",
    val dns: String? = null,
    val dnsLatencyMillis: Long? = null,
    val pingMillis: Long? = null,
    val jitterMillis: Long? = null,
    val lossPercent: Int? = null,
    val stabilityScore: Double? = null,
    val routeScore: Double? = null,
    val status: String = "INACTIVE",
    val routeLock: Boolean = false,
    val lastCheckMillis: Long? = null,
    val modeEnabled: Boolean = false,
    val selectedAppCount: Int = 0,
    val vpnActive: Boolean = false,
    val perAppRoutingActive: Boolean = false,
    /** Wall-clock time the engine published this snapshot; 0 means it was never published. */
    val publishedAtMillis: Long = 0L
) {
    companion object {
        /**
         * How long a published snapshot still counts as coming from a live engine. The engines
         * re-publish at least once per check cycle, so a snapshot older than this belongs to a
         * `:daemon` process that is gone (crash or kill) and must not be shown as current.
         */
        const val SNAPSHOT_MAX_AGE_MILLIS = 180_000L

        /** Nothing measured: every metric stays null so the UI renders it as "N/A". */
        fun idle(mode: String, serviceRunning: Boolean = false): OptimizeDiagnostics =
            OptimizeDiagnostics(mode = mode, label = "N/A", status = "INACTIVE", vpnActive = serviceRunning)

        /**
         * Publishes [snapshot] under [key] for the UI process. The engines run in the `:daemon`
         * process, the Diagnostics screen in the main process, so an in-memory flow is never
         * shared between them; the multi-process store is the only bridge.
         *
         * @return The snapshot as published, with its timestamp.
         */
        fun publish(key: String, snapshot: OptimizeDiagnostics): OptimizeDiagnostics {
            val stamped = snapshot.copy(publishedAtMillis = System.currentTimeMillis())
            MmkvManager.encodeDiagnosticsSnapshot(key, JsonUtil.toJson(stamped))
            return stamped
        }

        /** Reads what the engine last published under [key], or null if nothing was. */
        fun read(key: String): OptimizeDiagnostics? {
            val json = MmkvManager.decodeDiagnosticsSnapshot(key) ?: return null
            return JsonUtil.fromJsonSafe(json, OptimizeDiagnostics::class.java)
        }

        /**
         * Decides what the UI process shows. [modeEnabled] and [selectedAppCount] come from the
         * settings store, which both processes share, so they always match what the user just
         * toggled; everything measured comes from the engine's [published] snapshot, and only
         * while that snapshot is recent enough to belong to a running engine.
         */
        fun resolve(
            published: OptimizeDiagnostics?,
            mode: String,
            modeEnabled: Boolean,
            selectedAppCount: Int,
            nowMillis: Long
        ): OptimizeDiagnostics {
            val live = published?.takeIf {
                it.publishedAtMillis > 0L && nowMillis - it.publishedAtMillis in 0L..SNAPSHOT_MAX_AGE_MILLIS
            }
            val base = when {
                live == null -> idle(mode)
                // The engine is running but the mode is off: its last measured values are stale.
                !modeEnabled -> idle(mode, serviceRunning = true)
                else -> live
            }
            return base.copy(modeEnabled = modeEnabled, selectedAppCount = selectedAppCount)
        }
    }
}

data class DnsDiagnostics(
    val currentDns: String? = null,
    val dnsResponseMillis: Long? = null,
    val successRatePercent: Int? = null,
    val dnsScore: Double? = null,
    val lastBenchmarkMillis: Long? = null
)
