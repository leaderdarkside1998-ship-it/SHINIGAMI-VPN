package com.v2ray.ang.shinigami

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil

/**
 * Weight of each measured factor in a preset's 0..100 score. Values are relative and get
 * normalized internally, so callers don't need them to sum to any particular total.
 *
 * Factors used:
 * - latency: lower measured ping is better
 * - jitter: lower measured jitter is better
 * - packetLoss: lower measured packet loss is better
 * - stability: [RouteMetrics.stabilityScore], already a composite of the three above
 * - successRate: how many of this server's recent real-ping probes succeeded at all
 */
data class PresetWeight(
    val latency: Double,
    val jitter: Double,
    val packetLoss: Double,
    val stability: Double,
    val successRate: Double
)

/**
 * Holds one [PresetWeight] per [ShinigamiPreset]. Persisted as JSON in the app's settings store
 * so it is a real, editable configuration rather than a hard-coded switch - see [load]/[save].
 */
data class ShinigamiWeightsConfig(
    val gaming: PresetWeight,
    val instagram: PresetWeight,
    val download: PresetWeight,
    val voiceCall: PresetWeight,
    val streaming: PresetWeight
) {
    fun forPreset(preset: ShinigamiPreset): PresetWeight = when (preset) {
        ShinigamiPreset.GAMING -> gaming
        ShinigamiPreset.INSTAGRAM -> instagram
        ShinigamiPreset.DOWNLOAD -> download
        ShinigamiPreset.VOICE_CALL -> voiceCall
        ShinigamiPreset.STREAMING -> streaming
    }

    companion object {
        private const val SETTINGS_KEY = "pref_shinigami_weights_json"

        /**
         * Defaults follow the priority order from the SHINIGAMI spec for each preset. These are
         * only the *starting* configuration - [load] always prefers whatever is persisted, and
         * [save] lets that persisted configuration be changed at runtime.
         */
        val DEFAULT = ShinigamiWeightsConfig(
            // Gaming: latency > loss > jitter > stability > success
            gaming = PresetWeight(latency = 0.35, jitter = 0.15, packetLoss = 0.25, stability = 0.15, successRate = 0.10),
            // Instagram: stability + latency + loss + throughput(proxy: successRate) balanced
            instagram = PresetWeight(latency = 0.25, jitter = 0.10, packetLoss = 0.20, stability = 0.30, successRate = 0.15),
            // Download: stability/throughput-heavy, low weight on raw latency
            download = PresetWeight(latency = 0.10, jitter = 0.05, packetLoss = 0.25, stability = 0.40, successRate = 0.20),
            // Voice call: latency/jitter/loss dominate, throughput irrelevant
            voiceCall = PresetWeight(latency = 0.30, jitter = 0.30, packetLoss = 0.25, stability = 0.10, successRate = 0.05),
            // Streaming: stability-heavy, ping alone must not decide it
            streaming = PresetWeight(latency = 0.10, jitter = 0.05, packetLoss = 0.20, stability = 0.45, successRate = 0.20)
        )

        fun load(): ShinigamiWeightsConfig {
            val json = MmkvManager.decodeSettingsString(SETTINGS_KEY) ?: return DEFAULT
            return JsonUtil.fromJsonSafe(json, ShinigamiWeightsConfig::class.java) ?: DEFAULT
        }

        fun save(config: ShinigamiWeightsConfig) {
            MmkvManager.encodeSettings(SETTINGS_KEY, JsonUtil.toJson(config))
        }
    }
}
