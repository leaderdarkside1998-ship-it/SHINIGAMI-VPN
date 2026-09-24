package com.v2ray.ang.ui.main

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingResultClockTest {

    @After
    fun tearDown() = PingResultClock.clearForTest()

    /**
     * Regression: a server that was on screen for an old test kept that test's stamp while it was
     * scrolled out of view during the next "test all", so its fresh result showed as expired.
     */
    @Test
    fun offScreenServerGetsFreshStampWhenItsResultIsApplied() {
        val t0 = 0L
        val minutes = 60_000L
        PingResultClock.record(mapOf("a" to 40L), now = t0)

        // 10 minutes later a new test resets, then finishes, without any card composing.
        PingResultClock.record(mapOf("a" to 0L), now = t0 + 10 * minutes)
        PingResultClock.record(mapOf("a" to 55L), now = t0 + 10 * minutes + 2_000L)

        val now = t0 + 10 * minutes + 5_000L
        assertTrue(
            PingResultClock.isResultVisible(55L, true, PingResultClock.arrivedAt("a"), now)
        )
    }

    @Test
    fun resultExpiresFiveMinutesAfterItArrived() {
        PingResultClock.record(mapOf("a" to 40L), now = 1_000L)
        val arrived = PingResultClock.arrivedAt("a")
        assertTrue(PingResultClock.isResultVisible(40L, true, arrived, 1_000L + 299_999L))
        assertFalse(PingResultClock.isResultVisible(40L, true, arrived, 1_000L + 300_000L))
    }

    @Test
    fun autoHideOffKeepsResultVisibleAndNoResultNeverShows() {
        assertTrue(PingResultClock.isResultVisible(40L, false, 0L, 10_000_000L))
        assertFalse(PingResultClock.isResultVisible(0L, false, null, 0L))
        assertFalse(PingResultClock.isResultVisible(0L, true, null, 0L))
    }

    @Test
    fun failedResultCountsAsAResultAndStampsToo() {
        PingResultClock.record(mapOf("a" to -1L), now = 5L)
        assertEquals(5L, PingResultClock.arrivedAt("a"))
    }

    @Test
    fun resetForgetsStampAndRecordIfAbsentDoesNotOverwrite() {
        PingResultClock.record(mapOf("a" to 40L), now = 5L)
        PingResultClock.recordIfAbsent("a", now = 99L)
        assertEquals(5L, PingResultClock.arrivedAt("a"))

        PingResultClock.record(mapOf("a" to 0L), now = 6L)
        assertNull(PingResultClock.arrivedAt("a"))
        PingResultClock.recordIfAbsent("a", now = 99L)
        assertEquals(99L, PingResultClock.arrivedAt("a"))
    }

    @Test
    fun unknownArrivalCountsAsBrandNew() {
        assertTrue(PingResultClock.isResultVisible(40L, true, null, 123_456_789L))
    }
}
