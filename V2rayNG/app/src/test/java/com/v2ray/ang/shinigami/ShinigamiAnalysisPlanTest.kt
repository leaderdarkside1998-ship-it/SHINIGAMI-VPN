package com.v2ray.ang.shinigami

import org.junit.Assert.assertEquals
import org.junit.Test

class ShinigamiAnalysisPlanTest {

    @Test
    fun onlyServersThatAnsweredAtLeastOnceAreProbedAgain() {
        val samples = mapOf(
            "a" to listOf(120L),
            "b" to listOf(-1L),
            "c" to listOf(-1L, 90L),
            "d" to emptyList()
        )
        assertEquals(listOf("a", "c"), ShinigamiAnalysisPlan.respondingServers(listOf("a", "b", "c", "d", "e"), samples))
    }

    @Test
    fun theOrderOfTheCandidateListIsKept() {
        val samples = mapOf("x" to listOf(10L), "y" to listOf(20L))
        assertEquals(listOf("y", "x"), ShinigamiAnalysisPlan.respondingServers(listOf("y", "x"), samples))
    }

    @Test
    fun progressGrowsAcrossRoundsAndNeverLeavesZeroToOne() {
        assertEquals(0f, ShinigamiAnalysisPlan.progress(1, 0, 10), 0.0001f)
        assertEquals(1f / 6f, ShinigamiAnalysisPlan.progress(1, 5, 10), 0.0001f)
        assertEquals(1f / 3f, ShinigamiAnalysisPlan.progress(2, 0, 10), 0.0001f)
        assertEquals(1f, ShinigamiAnalysisPlan.progress(ShinigamiAnalysisPlan.ROUNDS, 10, 10), 0.0001f)
        assertEquals(1f, ShinigamiAnalysisPlan.progress(ShinigamiAnalysisPlan.ROUNDS, 99, 10), 0.0001f)
    }

    @Test
    fun anEmptyRoundCountsAsComplete() {
        assertEquals(1f / 3f, ShinigamiAnalysisPlan.progress(1, 0, 0), 0.0001f)
    }

    @Test
    fun networkSnapshotIsDescribedOnOneLine() {
        val snapshot = ShinigamiNetworkSnapshot(
            transport = "Mobile",
            cellularGeneration = "4G",
            isMetered = true,
            isInternetValidated = true,
            downstreamKbps = 25_000,
            upstreamKbps = null,
            signalStrengthLevel = null
        )
        assertEquals("Mobile 4G, down 25 Mbps, metered", snapshot.describe())
        assertEquals(
            "Wi-Fi, not validated",
            snapshot.copy(
                transport = "Wi-Fi",
                cellularGeneration = null,
                isMetered = false,
                isInternetValidated = false,
                downstreamKbps = null
            ).describe()
        )
    }
}
