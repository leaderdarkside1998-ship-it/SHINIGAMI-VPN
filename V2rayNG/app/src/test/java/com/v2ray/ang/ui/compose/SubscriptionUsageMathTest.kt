package com.v2ray.ang.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SubscriptionUsageMathTest {

    @Before
    fun useStableLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun fractionIsProportionalToUsedOverTotal() {
        assertEquals(0.25f, usageFraction(256L, 1024L), 0.0001f)
    }

    @Test
    fun fractionIsZeroForZeroUsage() {
        assertEquals(0f, usageFraction(0L, 1024L), 0f)
    }

    @Test
    fun fractionIsClampedWhenTrafficIsExhaustedOrOverrun() {
        assertEquals(1f, usageFraction(1024L, 1024L), 0f)
        assertEquals(1f, usageFraction(4096L, 1024L), 0f)
    }

    @Test
    fun fractionIsZeroWhenLimitIsUnknownOrUnlimited() {
        assertEquals(0f, usageFraction(500L, null), 0f)
        assertEquals(0f, usageFraction(500L, 0L), 0f)
    }

    @Test
    fun formatBytesKeepsExistingUnitsAndPrecision() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1.00 KiB", formatBytes(1024L))
        assertEquals("6.87 GiB", formatBytes((6.87 * 1024 * 1024 * 1024).toLong()))
    }
}
