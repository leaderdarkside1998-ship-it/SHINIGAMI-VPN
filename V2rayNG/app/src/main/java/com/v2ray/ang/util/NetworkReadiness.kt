package com.v2ray.ang.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.delay
import com.v2ray.ang.handler.SpeedtestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Tells whether the phone has a working internet connection right now, and helps it get one back.
 *
 * A phone call (even one that is only ringing, answered or declined) pushes many phones from LTE
 * down to H+/3G and often cuts mobile data for a while. When the call ends the radio comes back
 * with a new bearer, but Android keeps the network flagged as "not validated" until its own
 * connectivity check passes again. Anything measured in that window (a TCP connect with a 1 second
 * timeout, a real-delay probe) fails and is stored as -1, even though the servers are fine. This is
 * why tests only worked again after a big group test had generated enough traffic to wake the
 * connection up.
 *
 * Nothing here needs the READ_PHONE_STATE permission: it only looks at the connectivity state.
 */
object NetworkReadiness {

    private const val POLL_INTERVAL_MS = 400L
    private const val WARM_UP_TIMEOUT_MS = 1200
    private const val RECENT_WINDOW_MS = 45_000L

    /** Public anycast endpoints, only used to push a little traffic through a sleeping data bearer. */
    private val WARM_UP_TARGETS = listOf(
        "1.1.1.1" to 443,
        "8.8.8.8" to 443,
        "9.9.9.9" to 443,
    )

    @Volatile
    private var lastUnsettledAt = 0L

    /**
     * True when a non-VPN network with a validated internet connection exists.
     * If the state cannot be read the answer is true, so a failure here never blocks a test.
     */
    fun isValidated(context: Context): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return try {
            @Suppress("DEPRECATION")
            connectivity.allNetworks.any { network ->
                val caps = connectivity.getNetworkCapabilities(network) ?: return@any false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "NetworkReadiness: cannot read network state", e)
            true
        }
    }

    /** True if [settle] had to wait for the network during the last few seconds. */
    fun recentlyUnsettled(): Boolean =
        lastUnsettledAt != 0L && SystemClock.elapsedRealtime() - lastUnsettledAt < RECENT_WINDOW_MS

    /**
     * Sends a few short TCP connects in parallel. The results do not matter, the traffic makes the
     * modem re-establish a sleeping data bearer and lets Android's own connectivity check re-run.
     */
    suspend fun warmUp() {
        coroutineScope {
            WARM_UP_TARGETS.map { (host, port) ->
                async(Dispatchers.IO) { SpeedtestManager.socketConnectTime(host, port, WARM_UP_TIMEOUT_MS) }
            }.awaitAll()
        }
    }

    /**
     * Returns immediately when the network is already validated. Otherwise warms the connection up
     * and waits up to [timeoutMs] for Android to validate it again. Never throws and never waits
     * longer than [timeoutMs] (plus the warm-up), so tests always go ahead in the end.
     *
     * @return True if the network was not ready when this was called.
     */
    suspend fun settle(context: Context, timeoutMs: Long = 6000L): Boolean {
        if (isValidated(context)) return false

        lastUnsettledAt = SystemClock.elapsedRealtime()
        LogUtil.i(AppConfig.TAG, "NetworkReadiness: network not validated, warming up")
        warmUp()

        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var ready = isValidated(context)
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            delay(POLL_INTERVAL_MS)
            ready = isValidated(context)
        }
        LogUtil.i(AppConfig.TAG, "NetworkReadiness: settle finished, validated=$ready")
        return true
    }
}
