package com.v2ray.ang.shinigami

/**
 * Pure decisions of one SHINIGAMI analysis run, kept apart from the ViewModel so they can be
 * covered by JVM tests.
 *
 * A run pings every saved server up to [ROUNDS] times. Servers that never answered are not worth
 * more rounds, so only the ones that answered at least once are probed again; that keeps a
 * long server list from spending most of the run on dead servers.
 */
internal object ShinigamiAnalysisPlan {

    const val ROUNDS = 3

    /** Upper bound for the whole run; results collected until then are still scored. */
    const val TIME_BUDGET_MILLIS = 180_000L

    /** Servers of [guids] that answered at least one probe so far (a negative delay is a failure). */
    fun respondingServers(guids: List<String>, samples: Map<String, List<Long>>): List<String> =
        guids.filter { guid -> samples[guid].orEmpty().any { it >= 0 } }

    /** Overall progress 0..1 while probing [done] of [total] servers in the 1-based [round]. */
    fun progress(round: Int, done: Int, total: Int): Float {
        val inRound = if (total > 0) done.toFloat() / total else 1f
        return (((round - 1) + inRound) / ROUNDS).coerceIn(0f, 1f)
    }
}
