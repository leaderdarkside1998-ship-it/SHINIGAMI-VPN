package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.service.RealPingWorkerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Real ping/jitter/packet-loss metrics for one server, computed from actual network probes
 * (reusing the same [RealPingWorkerService] the manual "real ping" test button uses). Nothing
 * here is fabricated: a route that can't be probed returns [isMeasurable] = false and every
 * numeric field stays at its N/A sentinel (-1).
 */
data class RouteMetrics(
    val guid: String,
    val pingMillis: Long = -1L,
    val jitterMillis: Long = -1L,
    val lossPercent: Int = -1,
    val stabilityScore: Double = -1.0,
    val sampleCount: Int = 0
) {
    val isMeasurable: Boolean get() = pingMillis >= 0
}

object StabilityMeter {

    /** Upper bound for one probe, so a probe that never reports back can't freeze the engine loop. */
    private const val PROBE_TIMEOUT_MILLIS = 20_000L

    /**
     * Sends [samples] real, individually-timed probes to [guid] (spaced [spacingMillis] apart so
     * they reflect real conditions over a short window rather than one burst) and derives:
     * - ping: mean of successful probes
     * - jitter: standard deviation of successful probes
     * - packet loss: percentage of probes that failed/timed out
     * - stability score: 0..1000, higher is better, derived only from the three real values above
     */
    suspend fun measure(context: Context, guid: String, samples: Int = 5, spacingMillis: Long = 250): RouteMetrics {
        if (samples <= 0) return RouteMetrics(guid)
        val results = ArrayList<Long>(samples)
        repeat(samples) { index ->
            val delayMillis = try {
                singlePing(context, guid)
            } catch (_: Exception) {
                -1L
            }
            results.add(delayMillis)
            if (index != samples - 1) delay(spacingMillis)
        }

        val successes = results.filter { it >= 0 }
        val lossPercent = ((results.size - successes.size) * 100) / results.size
        if (successes.isEmpty()) {
            return RouteMetrics(guid = guid, lossPercent = 100, sampleCount = results.size)
        }

        val mean = successes.average()
        val jitter = if (successes.size > 1) {
            sqrt(successes.sumOf { (it - mean) * (it - mean) } / successes.size)
        } else 0.0

        // Real-valued weighted score: 1000 is perfect (0 ping, 0 jitter, 0 loss). Each component
        // is a direct penalty from a measured value, nothing guessed or randomized.
        val score = (1000.0 - mean - (jitter * 2.0) - (lossPercent * 15.0)).coerceAtLeast(0.0)

        return RouteMetrics(
            guid = guid,
            pingMillis = mean.toLong(),
            jitterMillis = jitter.toLong(),
            lossPercent = lossPercent,
            stabilityScore = score,
            sampleCount = results.size
        )
    }

    private suspend fun singlePing(context: Context, guid: String): Long =
        withTimeoutOrNull(PROBE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Long> { cont ->
                var worker: RealPingWorkerService? = null
                worker = RealPingWorkerService(context, listOf(guid), onlyTcp = false) { event ->
                    when (event) {
                        is RealPingEvent.Result -> if (cont.isActive) cont.resume(event.delayMillis)
                        // The worker swallows a probe that throws without sending a Result, so
                        // Finish is the only signal that this probe is over: count it as failed.
                        is RealPingEvent.Finish -> if (cont.isActive) cont.resume(-1L)
                        else -> Unit
                    }
                }
                cont.invokeOnCancellation { worker?.cancel() }
                worker.start()
            }
        } ?: -1L
}
