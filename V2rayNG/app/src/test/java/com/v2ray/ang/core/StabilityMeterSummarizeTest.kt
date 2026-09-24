package com.v2ray.ang.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityMeterSummarizeTest {

    @Test
    fun aServerThatWasNeverProbedIsUnmeasurableWithoutClaimingLoss() {
        val metrics = StabilityMeter.summarize("g", emptyList())
        assertFalse(metrics.isMeasurable)
        assertEquals(-1, metrics.lossPercent)
        assertEquals(0, metrics.sampleCount)
    }

    @Test
    fun aServerWhoseEveryProbeFailedIsUnmeasurableWithFullLoss() {
        val metrics = StabilityMeter.summarize("g", listOf(-1L, -1L, -1L))
        assertFalse(metrics.isMeasurable)
        assertEquals(100, metrics.lossPercent)
        assertEquals(3, metrics.sampleCount)
    }

    @Test
    fun pingIsTheMeanAndJitterTheStandardDeviationOfSuccessfulProbes() {
        val metrics = StabilityMeter.summarize("g", listOf(100L, 200L))
        assertTrue(metrics.isMeasurable)
        assertEquals(150L, metrics.pingMillis)
        assertEquals(50L, metrics.jitterMillis)
        assertEquals(0, metrics.lossPercent)
        // 1000 - mean - 2 * jitter - 15 * loss
        assertEquals(750.0, metrics.stabilityScore, 0.001)
    }

    @Test
    fun failedProbesCountAsLossButNotAsLatency() {
        val metrics = StabilityMeter.summarize("g", listOf(100L, -1L, 300L, 200L))
        assertEquals(200L, metrics.pingMillis)
        assertEquals(25, metrics.lossPercent)
        assertEquals(4, metrics.sampleCount)
        assertTrue(metrics.stabilityScore < 300.0)
    }

    @Test
    fun aSingleSuccessfulProbeHasNoJitter() {
        val metrics = StabilityMeter.summarize("g", listOf(120L, -1L, -1L))
        assertEquals(120L, metrics.pingMillis)
        assertEquals(0L, metrics.jitterMillis)
        assertEquals(66, metrics.lossPercent)
    }
}
