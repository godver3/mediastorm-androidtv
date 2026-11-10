package com.strmr.tv.ui.sports

import com.strmr.tv.sports.SportsLifecycle

/** The hub's rows, in the order they show (Expo `SportsHubTV` sections). */
enum class SportsRow(val key: String, val title: String) {
    LIVE("live", "Live Now"), UPCOMING("upcoming", "Upcoming"), RESULTS("results", "Results"), CHANGES("changes", "Schedule Updates");

    /** Whether a card in this lifecycle belongs in this row. */
    fun contains(lifecycle: SportsLifecycle): Boolean = when (this) {
        LIVE -> lifecycle == SportsLifecycle.LIVE
        UPCOMING -> lifecycle == SportsLifecycle.SCHEDULED
        RESULTS -> lifecycle == SportsLifecycle.FINAL
        CHANGES -> lifecycle.isScheduleChange
    }
}

/**
 * The hub's order within each row (Expo `SportsHubShowcase`'s stable identity order, tvOS
 * `SportsHubOrdering`): a card takes a place the first time a load lists it, by start and then
 * key, and keeps it, so score and clock updates, and games that start together, never swap cards
 * between refreshes. Upcoming cards follow their start (a delay moves one, as in Expo), with the
 * place breaking ties; results show the latest first. Followed competitions lead every row (the
 * rank). Not thread-safe: the hub records and reads it on the main thread.
 */
class SportsHubOrdering {
    private val places = HashMap<String, Int>()

    /** Gives each card not seen before the next place, in start-then-key order. */
    fun record(items: List<SportsHubItem>) {
        for (item in items.sortedWith(byStart)) if (item.key !in places) places[item.key] = places.size
    }

    /** Forgets every place (another day). */
    fun reset() = places.clear()

    fun place(item: SportsHubItem): Int = places[item.key] ?: Int.MAX_VALUE

    /** A row's cards in the order the hub shows them. */
    fun ordered(items: List<SportsHubItem>, row: SportsRow, rank: (SportsHubItem) -> Int): List<SportsHubItem> {
        val within: Comparator<SportsHubItem> = when (row) {
            SportsRow.UPCOMING -> compareBy<SportsHubItem> { it.start }.thenBy(::place)
            SportsRow.RESULTS -> compareByDescending(::place)
            SportsRow.LIVE, SportsRow.CHANGES -> compareBy(::place)
        }
        return items.sortedWith(compareBy(rank).then(within))
    }

    companion object {
        /** By start, then key: one order for cards that start together, whatever order the feed sends. */
        val byStart: Comparator<SportsHubItem> = compareBy<SportsHubItem> { it.start }.thenBy { it.key }
    }
}
