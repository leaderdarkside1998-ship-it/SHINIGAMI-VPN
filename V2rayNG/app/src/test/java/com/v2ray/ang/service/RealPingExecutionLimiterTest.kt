package com.v2ray.ang.service

import com.v2ray.ang.enums.EConfigType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RealPingExecutionLimiterTest {

    @Test
    fun customConfigMeasurementsAreSerializedAcrossWorkers() {
        runBlocking {
            val active = AtomicInteger(0)
            val maxActive = AtomicInteger(0)

            List(8) {
                async(Dispatchers.Default) {
                    RealPingExecutionLimiter.run(EConfigType.CUSTOM) {
                        val current = active.incrementAndGet()
                        maxActive.accumulateAndGet(current, ::maxOf)
                        Thread.sleep(20)
                        active.decrementAndGet()
                    }
                }
            }.awaitAll()

            assertEquals(1, maxActive.get())
        }
    }

    @Test
    fun generatedConfigMeasurementsAreAlsoSerialized() {
        // Every config type shares the same native probe, which is not safe to run
        // concurrently, so generated (non-custom) configs must be serialized too.
        runBlocking {
            val active = AtomicInteger(0)
            val maxActive = AtomicInteger(0)

            List(8) {
                async(Dispatchers.Default) {
                    RealPingExecutionLimiter.run(EConfigType.VMESS) {
                        val current = active.incrementAndGet()
                        maxActive.accumulateAndGet(current, ::maxOf)
                        Thread.sleep(20)
                        active.decrementAndGet()
                    }
                }
            }.awaitAll()

            assertEquals(1, maxActive.get())
        }
    }
}
