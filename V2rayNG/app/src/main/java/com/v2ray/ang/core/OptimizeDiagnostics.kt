package com.v2ray.ang.core

/** Real-time, real-measurement-only snapshot shown by the Diagnostics panel. Any field that
 * can't be genuinely measured on this device/network is left null and the UI must render it as
 * "N/A" rather than guessing a value. */
data class OptimizeDiagnostics(
    val mode: String,
    val label: String,
    val routeGuid: String? = null,
    val routeName: String? = null,
    val server: String? = null,
    val transport: String? = null,
    val network: String = "IPv4",
    val dns: String? = null,
    val dnsLatencyMillis: Long? = null,
    val pingMillis: Long? = null,
    val jitterMillis: Long? = null,
    val lossPercent: Int? = null,
    val stabilityScore: Double? = null,
    val routeScore: Double? = null,
    val status: String = "INACTIVE",
    val routeLock: Boolean = false,
    val lastCheckMillis: Long? = null,
    val modeEnabled: Boolean = false,
    val selectedAppCount: Int = 0,
    val vpnActive: Boolean = false,
    val perAppRoutingActive: Boolean = false
)

data class DnsDiagnostics(
    val currentDns: String? = null,
    val dnsResponseMillis: Long? = null,
    val successRatePercent: Int? = null,
    val dnsScore: Double? = null,
    val lastBenchmarkMillis: Long? = null
)
