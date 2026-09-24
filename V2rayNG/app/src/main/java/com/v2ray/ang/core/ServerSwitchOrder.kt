package com.v2ray.ang.core

/**
 * Decides which saved server the notification's "Switch server" action moves to.
 *
 * Kept apart from [CoreServiceManager] (which loads the native core) because it is a pure
 * decision that can be covered by a JVM test.
 */
internal object ServerSwitchOrder {

    /**
     * Returns the first usable server after [currentGuid] in [guids] order, wrapping from the
     * end of the list back to the start, so repeated calls visit every usable server in turn.
     * When [currentGuid] is null or no longer in the list the walk starts from the first entry.
     * Returns null when there is no usable server other than [currentGuid].
     */
    fun next(guids: List<String>, currentGuid: String?, isUsable: (String) -> Boolean): String? {
        val ordered = guids.distinct()
        if (ordered.isEmpty()) return null
        val start = ordered.indexOf(currentGuid)
        for (step in 1..ordered.size) {
            val candidate = ordered[(start + step) % ordered.size]
            if (candidate != currentGuid && isUsable(candidate)) return candidate
        }
        return null
    }
}
