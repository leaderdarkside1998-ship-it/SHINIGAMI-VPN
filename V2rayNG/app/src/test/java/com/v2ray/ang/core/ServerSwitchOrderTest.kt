package com.v2ray.ang.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerSwitchOrderTest {

    private val all: (String) -> Boolean = { true }

    @Test
    fun repeatedSwitchesVisitEveryServerOnceBeforeRepeating() {
        val servers = listOf("a", "b", "c", "d", "e", "f")
        var current: String? = "a"
        val visited = mutableListOf<String>()
        repeat(servers.size) {
            current = ServerSwitchOrder.next(servers, current, all)
            visited.add(current!!)
        }
        assertEquals(listOf("b", "c", "d", "e", "f", "a"), visited)
    }

    @Test
    fun theLastServerWrapsAroundToTheFirst() {
        assertEquals("a", ServerSwitchOrder.next(listOf("a", "b", "c"), "c", all))
    }

    @Test
    fun aMissingOrUnknownCurrentServerStartsFromTheFirst() {
        assertEquals("a", ServerSwitchOrder.next(listOf("a", "b"), null, all))
        assertEquals("a", ServerSwitchOrder.next(listOf("a", "b"), "gone", all))
    }

    @Test
    fun unusableServersAreSkippedButStillCycled() {
        val usable = setOf("a", "d")
        val isUsable: (String) -> Boolean = { it in usable }
        val servers = listOf("a", "b", "c", "d")
        assertEquals("d", ServerSwitchOrder.next(servers, "a", isUsable))
        assertEquals("a", ServerSwitchOrder.next(servers, "d", isUsable))
    }

    @Test
    fun duplicatesInTheListDoNotCauseRepeatedStops() {
        val servers = listOf("a", "b", "a", "b", "c")
        assertEquals("b", ServerSwitchOrder.next(servers, "a", all))
        assertEquals("c", ServerSwitchOrder.next(servers, "b", all))
        assertEquals("a", ServerSwitchOrder.next(servers, "c", all))
    }

    @Test
    fun noOtherUsableServerReturnsNull() {
        assertNull(ServerSwitchOrder.next(emptyList(), "a", all))
        assertNull(ServerSwitchOrder.next(listOf("a"), "a", all))
        assertNull(ServerSwitchOrder.next(listOf("a", "b"), "a") { it == "a" })
    }
}
