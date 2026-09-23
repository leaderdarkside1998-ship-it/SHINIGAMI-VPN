package com.v2ray.ang.shinigami

import com.v2ray.ang.core.RouteMetrics
import kotlin.math.roundToInt

/**
 * Turns real, already-measured [RouteMetrics] into a 0..100 score for one [ShinigamiPreset],
 * using the weights in [ShinigamiWeightsConfig]. The engine never fabricates a metric: a server
 * that couldn't be measured ([RouteMetrics.isMeasurable] == false) gets a null score and is
 * never recommended (spec requirement - AI/engine may only pick from real, working servers).
 */
object ShinigamiScoringEngine {

    // Normalization ceilings: measured values at/above these are treated as "worst case" (0
    // points for that factor). Chosen from typical mobile-proxy ranges, not from any one probe.
    private const val LATENCY_CEILING_MS = 600.0
    private const val JITTER_CEILING_MS = 150.0
    private const val LOSS_CEILING_PERCENT = 100.0
    private const val STABILITY_CEILING = 1000.0

    fun score(metrics: RouteMetrics, weight: PresetWeight, sampleCount: Int): Int? {
        if (!metrics.isMeasurable) return null

        val latencyPts = invertedFraction(metrics.pingMillis.toDouble(), LATENCY_CEILING_MS)
        val jitterPts = invertedFraction(metrics.jitterMillis.toDouble().coerceAtLeast(0.0), JITTER_CEILING_MS)
        val lossPts = invertedFraction(metrics.lossPercent.toDouble().coerceAtLeast(0.0), LOSS_CEILING_PERCENT)
        val stabilityPts = (metrics.stabilityScore.coerceIn(0.0, STABILITY_CEILING) / STABILITY_CEILING)
        val successRatePts = if (sampleCount > 0) {
            // lossPercent already reflects the same probes, so success rate is just its complement,
            // kept as its own weighted factor so callers can tune it independently of raw loss.
            ((100 - metrics.lossPercent).coerceIn(0, 100)) / 100.0
        } else 0.0

        val totalWeight = weight.latency + weight.jitter + weight.packetLoss + weight.stability + weight.successRate
        if (totalWeight <= 0.0) return null

        val weighted = (latencyPts * weight.latency) +
            (jitterPts * weight.jitter) +
            (lossPts * weight.packetLoss) +
            (stabilityPts * weight.stability) +
            (successRatePts * weight.successRate)

        return ((weighted / totalWeight) * 100.0).roundToInt().coerceIn(0, 100)
    }

    /** 1.0 when [value] is 0 (best), 0.0 when [value] >= [ceiling] (worst), linear between. */
    private fun invertedFraction(value: Double, ceiling: Double): Double =
        (1.0 - (value / ceiling)).coerceIn(0.0, 1.0)
}
