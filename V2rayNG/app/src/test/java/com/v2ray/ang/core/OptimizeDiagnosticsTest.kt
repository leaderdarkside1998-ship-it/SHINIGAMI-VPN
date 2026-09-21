package com.v2ray.ang.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimizeDiagnosticsTest {

    private val now = 1_000_000L

    private fun published(atMillis: Long, status: String = "ACTIVE") = OptimizeDiagnostics(
        mode = "GAMING",
        label = "com.example.game",
        routeName = "Turkey 1",
        pingMillis = 42L,
        jitterMillis = 3L,
        lossPercent = 0,
        stabilityScore = 950.0,
        status = status,
        vpnActive = true,
        publishedAtMillis = atMillis
    )

    private fun resolve(
        snapshot: OptimizeDiagnostics?,
        modeEnabled: Boolean = true,
        selectedAppCount: Int = 2
    ) = OptimizeDiagnostics.resolve(snapshot, "GAMING", modeEnabled, selectedAppCount, now)

    @Test
    fun noSnapshotShowsIdleButKeepsTheSettingsTheUserJustChanged() {
        val result = resolve(null)

        assertEquals("INACTIVE", result.status)
        assertFalse(result.vpnActive)
        assertNull(result.pingMillis)
        assertTrue(result.modeEnabled)
        assertEquals(2, result.selectedAppCount)
    }

    @Test
    fun recentSnapshotShowsTheEnginesMeasurements() {
        val result = resolve(published(now - 5_000L))

        assertEquals("ACTIVE", result.status)
        assertEquals(42L, result.pingMillis)
        assertEquals("Turkey 1", result.routeName)
        assertTrue(result.vpnActive)
    }

    @Test
    fun settingsOverrideTheSnapshotForEnabledFlagAndAppCount() {
        val result = resolve(published(now - 5_000L), modeEnabled = true, selectedAppCount = 7)

        assertEquals(7, result.selectedAppCount)
    }

    @Test
    fun snapshotAtExactlyTheMaxAgeIsStillLive() {
        val result = resolve(published(now - OptimizeDiagnostics.SNAPSHOT_MAX_AGE_MILLIS))

        assertEquals("ACTIVE", result.status)
    }

    @Test
    fun snapshotOlderThanTheMaxAgeIsTreatedAsADeadEngine() {
        val result = resolve(published(now - OptimizeDiagnostics.SNAPSHOT_MAX_AGE_MILLIS - 1))

        assertEquals("INACTIVE", result.status)
        assertNull(result.pingMillis)
        assertFalse(result.vpnActive)
    }

    @Test
    fun neverStampedSnapshotIsNotTrusted() {
        val result = resolve(published(atMillis = 0L))

        assertEquals("INACTIVE", result.status)
        assertNull(result.pingMillis)
    }

    @Test
    fun snapshotFromTheFutureIsNotTrusted() {
        val result = resolve(published(now + 60_000L))

        assertEquals("INACTIVE", result.status)
        assertNull(result.pingMillis)
    }

    @Test
    fun modeSwitchedOffHidesStaleMeasurementsButReportsTheServiceAsRunning() {
        val result = resolve(published(now - 5_000L), modeEnabled = false, selectedAppCount = 0)

        assertEquals("INACTIVE", result.status)
        assertNull(result.pingMillis)
        assertFalse(result.modeEnabled)
        assertTrue(result.vpnActive)
    }

    @Test
    fun idleSnapshotHasNoMeasurements() {
        val idle = OptimizeDiagnostics.idle("BOOST")

        assertEquals("BOOST", idle.mode)
        assertEquals("N/A", idle.label)
        assertEquals("INACTIVE", idle.status)
        assertNull(idle.pingMillis)
        assertFalse(idle.vpnActive)
    }
}
