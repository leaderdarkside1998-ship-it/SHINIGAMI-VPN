package com.v2ray.ang.ui.main

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers when each server's latest ping result arrived, keyed by server GUID.
 *
 * The result is written by the ViewModel at the moment a result is applied, for every server in
 * the tested group. It used to be written from the server card's own composable, and a lazy list
 * only composes the cards on screen. A card scrolled out of view during a "test all" therefore
 * kept the arrival time of an older test, and once it was scrolled back in, its fresh result was
 * judged more than [PING_RESULT_VISIBLE_MS] old and hidden behind the bolt icon. Only servers
 * that were on screen while the test ran showed a number.
 */
internal object PingResultClock {

    /** How long a fresh ping result stays visible (5 minutes) before the slot returns to the bolt icon. */
    const val PING_RESULT_VISIBLE_MS = 5 * 60 * 1000L

    private val arrivedAt = ConcurrentHashMap<String, Long>()

    /** Monotonic milliseconds; one time base for both the writers and the card that reads it. */
    fun now(): Long = System.nanoTime() / 1_000_000L

    /**
     * Applies a batch of results: a real value stamps that server's arrival time, 0 (the "no
     * result" marker a new test resets to) forgets it.
     */
    fun record(updates: Map<String, Long>, now: Long = now()) {
        updates.forEach { (guid, delayMillis) ->
            if (delayMillis == 0L) arrivedAt.remove(guid) else arrivedAt[guid] = now
        }
    }

    /** Stamps [guid] only if it has no arrival time yet (a value restored from storage). */
    fun recordIfAbsent(guid: String, now: Long = now()) {
        arrivedAt.putIfAbsent(guid, now)
    }

    fun arrivedAt(guid: String): Long? = arrivedAt[guid]

    /** Test hook: forgets every recorded arrival. */
    internal fun clearForTest() = arrivedAt.clear()

    /**
     * Whether a server's ping number is still shown. [arrivedAt] falls back to [now] when unknown,
     * so a result with no recorded arrival counts as brand new.
     */
    fun isResultVisible(
        delayMillis: Long,
        pingAutoHide: Boolean,
        arrivedAt: Long?,
        now: Long,
    ): Boolean = when {
        delayMillis == 0L -> false
        !pingAutoHide -> true
        else -> (now - (arrivedAt ?: now)) < PING_RESULT_VISIBLE_MS
    }
}
