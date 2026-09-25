package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.core.AetherDelayTester
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.NetworkReadiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal object RealPingExecutionLimiter {
    private val nativeProbeMutex = Mutex()

    /**
     * Every config type ends up calling the same native (JNI) outbound-delay probe in
     * [CoreNativeManager.measureOutboundDelay]. That native call is not safe to run
     * concurrently: overlapping probes can corrupt or abort each other's temporary Xray
     * instance, which surfaces as some servers in a batch silently failing or coming back
     * "invalid" even though they work fine when tested one at a time. Serialize every native
     * probe globally across batches; TCP-only pings never reach this path and keep running
     * fully concurrently.
     */
    suspend fun <T> run(configType: EConfigType, block: () -> T): T {
        return nativeProbeMutex.withLock { block() }
    }
}

/**
 * Worker that runs a batch of real-ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val concurrency = SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(if (onlyTcp) concurrency * 2 else concurrency).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    fun start() {
        scope.launch {
            try {
                // A recent phone call (ringing, answered, or declined -- it doesn't matter) can
                // leave the data connection flagged as "not validated" for a while even after
                // the call ends. Every probe below is a real TCP/JNI attempt with a short
                // timeout, so it fails and is stored as -1 while that flag is still down --
                // that's the "server invalid" / single-test-returns--1 bug. A big group test used
                // to "fix" this only as a side effect: enough parallel connections eventually
                // woke the radio and re-triggered Android's own validation. Do that on purpose,
                // once, before any real probe starts.
                NetworkReadiness.settle(context)

                val jobs = guids.map { guid ->
                    totalCount.incrementAndGet()
                    launch {
                        runningCount.incrementAndGet()
                        try {
                            val result = if (onlyTcp) startTcping(guid) else startRealPing(guid)
                            if (scope.isActive) {
                                onEvent(RealPingEvent.Result(guid, result))
                            }
                        } catch (_: Throwable) {
                            // ignore
                        } finally {
                            val count = totalCount.decrementAndGet()
                            val left = runningCount.decrementAndGet()
                            if (scope.isActive) {
                                onEvent(RealPingEvent.Progress("$left / $count"))
                            }
                        }
                    }
                }

                joinAll(*jobs.toTypedArray())
                if (isActive) {
                    onEvent(RealPingEvent.Finish("0"))
                }
            } catch (_: CancellationException) {
                // If cancelled, don't send finish event to avoid confusion
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private suspend fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (config.configType == EConfigType.AETHER) {
            return AetherDelayTester.measure(context, guid, config, SettingsManager.getDelayTestUrl())
        }
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.startsWith("h3") != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            val tcpTime = SpeedtestManager.socketConnectTime(url, port, 1000)
            if (tcpTime <= -1L) {
                return retFailure
            }
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return retFailure
        }
        return RealPingExecutionLimiter.run(config.configType) {
            CoreNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
        }
    }

    private fun startTcping(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (config.configType == EConfigType.AETHER) {
            return AetherDelayTester.reachability(config)
        }
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.split(',')?.all { it.trim().startsWith("h3") } != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            val tcpTime = SpeedtestManager.socketConnectTime(url, port, 1000)

            return tcpTime
        }

        return retFailure
    }
}
