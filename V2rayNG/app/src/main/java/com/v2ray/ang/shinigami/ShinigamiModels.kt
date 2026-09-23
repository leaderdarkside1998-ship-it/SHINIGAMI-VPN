package com.v2ray.ang.shinigami

import com.v2ray.ang.core.RouteMetrics

/**
 * The five presets SHINIGAMI supports. Each maps 1:1 to one of the five main options on the
 * SHINIGAMI home screen and to one weight profile in [ShinigamiWeights].
 */
enum class ShinigamiPreset {
    GAMING,
    INSTAGRAM,
    DOWNLOAD,
    VOICE_CALL,
    STREAMING;

    companion object {
        /** Best-effort keyword classification of free-form chat text into a preset, or null. */
        fun fromIntent(intent: String): ShinigamiPreset? =
            entries.firstOrNull { it.name == intent }
    }
}

/** Real, currently-available data about one server that SHINIGAMI is allowed to see. Nothing
 * here is a secret (no host/port/uuid/password) - see [ShinigamiServerAnalyzer]. */
data class ShinigamiServerSummary(
    val guid: String,
    val remarks: String,
    val subscriptionId: String,
    val protocol: String,
    val network: String?,
    val security: String?
)

/** One server's measured route metrics plus SHINIGAMI's derived score for a single preset. */
data class ShinigamiServerScore(
    val server: ShinigamiServerSummary,
    val metrics: RouteMetrics,
    /** 0..100, or null when the server could not be measured at all ([RouteMetrics.isMeasurable] false). */
    val score: Int?
)

/** A snapshot of the device's current network condition, gathered fresh for each analysis run.
 * Every field is either read from a real platform API or left null - nothing is guessed. */
data class ShinigamiNetworkSnapshot(
    val transport: String,         // "Wi-Fi" | "Mobile" | "Ethernet" | "VPN" | "Unknown" | "None"
    val cellularGeneration: String?, // "5G" | "4G" | "3G" | "2G" | null when not cellular/unknown
    val isMetered: Boolean?,
    val isInternetValidated: Boolean?,
    val downstreamKbps: Int?,      // NetworkCapabilities.linkDownstreamBandwidthKbps, -1 filtered out
    val upstreamKbps: Int?,
    val signalStrengthLevel: Int?  // 0..4 when the platform exposes it, else null
)

/** The full result of one SHINIGAMI analysis run for a given preset. */
data class ShinigamiAnalysisResult(
    val preset: ShinigamiPreset,
    val network: ShinigamiNetworkSnapshot,
    /** Sorted best-first by [ShinigamiServerScore.score] (unmeasurable servers last). */
    val ranked: List<ShinigamiServerScore>,
    val explanation: String,
    val usedAi: Boolean
) {
    val best: ShinigamiServerScore? get() = ranked.firstOrNull { it.score != null }
}
