package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelStatusTest {

    private val connecting = MainStatus.Connecting("The Aether core is still connecting")

    @Test
    fun aStartOrStopSignalSetsThePlainState() {
        assertEquals(
            MainStatus.Connected,
            MainViewModel.runningStatus(MainStatus.Disconnected, wasRunning = false, running = true, clearTestingText = false)
        )
        assertEquals(
            MainStatus.Disconnected,
            MainViewModel.runningStatus(MainStatus.Connected, wasRunning = true, running = false, clearTestingText = false)
        )
        assertEquals(
            MainStatus.Connected,
            MainViewModel.runningStatus(connecting, wasRunning = true, running = true, clearTestingText = true)
        )
        assertEquals(
            MainStatus.Disconnected,
            MainViewModel.runningStatus(connecting, wasRunning = true, running = false, clearTestingText = true)
        )
    }

    @Test
    fun aRepeatedSignalKeepsTestTextButEndsConnecting() {
        val progress = MainStatus.TestProgress("3 / 10")
        assertEquals(progress, MainViewModel.runningStatus(progress, wasRunning = true, running = true, clearTestingText = false))

        val result = MainStatus.ConnectionTest(ConnectionTestResult(delayMillis = 120L, errorMessage = ""))
        assertEquals(result, MainViewModel.runningStatus(result, wasRunning = true, running = true, clearTestingText = false))

        assertEquals(
            MainStatus.Connected,
            MainViewModel.runningStatus(connecting, wasRunning = true, running = true, clearTestingText = false)
        )
        assertEquals(
            MainStatus.Disconnected,
            MainViewModel.runningStatus(connecting, wasRunning = false, running = false, clearTestingText = false)
        )
    }

    @Test
    fun theTimerFollowsTheDaemonStartTimeAcrossReopeningTheApp() {
        // A fresh ViewModel (app reopened) learns the running session from the daemon.
        assertEquals(
            1_000L,
            MainViewModel.connectedSince(current = null, wasRunning = false, running = true, daemonStartedAtMillis = 1_000L, nowMillis = 9_000L)
        )
        // A repeated signal keeps the same start.
        assertEquals(
            1_000L,
            MainViewModel.connectedSince(current = 1_000L, wasRunning = true, running = true, daemonStartedAtMillis = 1_000L, nowMillis = 9_000L)
        )
    }

    @Test
    fun theTimerFallsBackToTheLocalClockOnlyWithoutADaemonTime() {
        assertEquals(
            9_000L,
            MainViewModel.connectedSince(current = null, wasRunning = false, running = true, daemonStartedAtMillis = null, nowMillis = 9_000L)
        )
        assertEquals(
            2_000L,
            MainViewModel.connectedSince(current = 2_000L, wasRunning = true, running = true, daemonStartedAtMillis = null, nowMillis = 9_000L)
        )
        assertEquals(
            9_000L,
            MainViewModel.connectedSince(current = null, wasRunning = false, running = true, daemonStartedAtMillis = 0L, nowMillis = 9_000L)
        )
    }

    @Test
    fun theTimerClearsWhenTheServiceStops() {
        assertEquals(
            null,
            MainViewModel.connectedSince(current = 1_000L, wasRunning = true, running = false, daemonStartedAtMillis = null, nowMillis = 9_000L)
        )
    }
}
