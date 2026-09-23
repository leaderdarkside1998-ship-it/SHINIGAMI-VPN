package com.v2ray.ang.shinigami

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Reads the device's current network condition from real Android platform APIs only. Every
 * field that can't actually be read on this device/OS/permission combination is left null
 * rather than guessed (spec requirement: "اطلاعات غیرقابل‌دسترسی جعل نشود").
 */
object ShinigamiNetworkAnalyzer {

    fun snapshot(context: Context): ShinigamiNetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val transport = when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Unknown"
        }

        val isMetered = caps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) }
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val downstreamKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }
        val upstreamKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 }

        val cellularGeneration = if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            cellularGeneration(context)
        } else null

        val signalLevel = if (transport == "Wi-Fi") wifiSignalLevel(context) else null

        return ShinigamiNetworkSnapshot(
            transport = transport,
            cellularGeneration = cellularGeneration,
            isMetered = isMetered,
            isInternetValidated = isValidated,
            downstreamKbps = downstreamKbps,
            upstreamKbps = upstreamKbps,
            signalStrengthLevel = signalLevel
        )
    }

    private fun cellularGeneration(context: Context): String? {
        val hasPhoneState = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPhoneState) return null
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
            when (networkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                else -> null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun wifiSignalLevel(context: Context): Int? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                ?: return null
            val rssi = wm.connectionInfo?.rssi ?: return null
            android.net.wifi.WifiManager.calculateSignalLevel(rssi, 5)
        } catch (_: Exception) {
            null
        }
    }
}
