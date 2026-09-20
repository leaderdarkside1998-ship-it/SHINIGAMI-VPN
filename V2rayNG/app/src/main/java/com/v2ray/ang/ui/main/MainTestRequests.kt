package com.v2ray.ang.ui.main

import java.util.UUID

/** Main-thread request ownership; a late reply must not complete a newer test. */
internal class MainTestRequests {
    data class Bulk(val id: String, val groupId: String)

    private var current: String? = null
    var bulk: Bulk? = null
        private set
    val isTesting: Boolean get() = current != null || bulk != null

    fun beginCurrent(): String = UUID.randomUUID().toString().also { current = it }

    fun completeCurrent(id: String): Boolean {
        if (id != current) return false
        current = null
        return true
    }

    fun invalidateCurrent() {
        current = null
    }

    fun beginBulk(groupId: String): Bulk = Bulk(UUID.randomUUID().toString(), groupId).also { bulk = it }

    fun completeBulk(id: String): Bulk? {
        val request = bulk?.takeIf { it.id == id } ?: return null
        bulk = null
        return request
    }

    fun cancelBulk() {
        bulk = null
    }

    /**
     * Single-server pings are independent of each other and of the bulk test, so several can be
     * in flight at once (tap server after server without waiting). Value = owning group id.
     */
    private val singles = mutableMapOf<String, String>()

    fun beginSingle(groupId: String): String = UUID.randomUUID().toString().also { singles[it] = groupId }

    fun singleGroup(id: String): String? = singles[id]

    fun completeSingle(id: String): String? = singles.remove(id)

    fun cancelSingles() {
        singles.clear()
    }
}
