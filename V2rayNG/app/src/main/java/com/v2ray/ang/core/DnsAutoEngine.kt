package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Periodically runs [DnsBenchmarkEngine] while [AppConfig.PREF_AUTO_DNS_ENABLED] is on. Separate
 * from the route-quality engines on purpose (see GAMING + DNS INTEGRATION note): DNS quality
 * (resolution time / success rate / consistency) is never used as a stand-in for route ping.
 */
object DnsAutoEngine {

    private val _diagnostics = MutableStateFlow(DnsDiagnostics())
    val diagnostics: StateFlow<DnsDiagnostics> = _diagnostics.asStateFlow()

    private var job: Job? = null
    private const val CHECK_INTERVAL_MILLIS = 60_000L

    fun start() {
        stop()
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                if (isEnabled()) {
                    try {
                        _diagnostics.value = DnsBenchmarkEngine.benchmarkAndApplyIfBetter()
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "DnsAutoEngine tick failed", e)
                    }
                }
                delay(CHECK_INTERVAL_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun isEnabled(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_DNS_ENABLED, false)
}
