package com.strmr.tv.player

/**
 * Which stream of a Stremio channel to try next, as Expo's `nextUntriedStremioStreamIndex`:
 * the first playable stream, in the addon's order, not tried yet in this player.
 */
class StremioFailover {
    /** The playable streams' indexes; null until the server or the stream list has said. */
    var available: List<Int>? = null
        private set
    private val tried = mutableSetOf<Int>()

    /** A tune of [index] (null: the server picked, which is the first playable stream). */
    fun tuned(index: Int?, available: List<Int>?) {
        if (!available.isNullOrEmpty()) this.available = available
        when {
            index != null -> tried += index
            tried.isEmpty() -> this.available?.firstOrNull()?.let { tried += it }
        }
    }

    fun learn(available: List<Int>) {
        this.available = available
        if (tried.isEmpty()) available.firstOrNull()?.let { tried += it }
    }

    /** The next untried stream, marked tried; null when every one has been. */
    fun next(): Int? = available?.firstOrNull { it !in tried }?.also { tried += it }
}
