package com.strmr.tv.home

import com.strmr.tv.api.ApiClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Each merged list's total under the sort and filters it was last read with, so later pages know where its titles fall. */
private val mergedTotals = ConcurrentHashMap<String, Int>()

/**
 * A streaming service's All part, as the Expo app shows a service: its lists (top movies, top
 * shows) merged, one title from each in turn, a list that runs out dropping from the turn. The
 * server sorts and filters each list on its own, so a sort orders each list within the merge
 * rather than the whole.
 */
internal suspend fun ApiClient.mergedServicePage(
    list: BrowseList.Service, limit: Int, offset: Int, extra: Map<String, String>, options: BrowseOptions?,
): BrowsePage = coroutineScope {
    val parts = list.service.lists.indices.map { list.copy(part = it + 1) }
    val keys = parts.map { part -> (part.query + extra + (options?.query(part) ?: emptyMap())).toSortedMap().toString() }
    var totals = keys.map { mergedTotals[it] }.takeIf { null !in it }?.map { it!! }
    var firstPages: List<BrowsePage>? = null
    if (offset == 0 || totals == null) {
        // Each list's first page gives its total, and holds every title the merged first page needs.
        val pages = parts.map { async { browsePage(it, maxOf(limit, 1), 0, extra, options = options) } }.awaitAll()
        keys.zip(pages).forEach { (key, page) -> mergedTotals[key] = page.total }
        totals = pages.map { it.total }
        firstPages = pages
    }
    val window = interleave(totals, offset, limit)
    val pages = if (firstPages != null && offset == 0) firstPages else parts.indices.map { i ->
        async {
            val (start, count) = window.ranges[i]
            if (count == 0) BrowsePage(emptyList(), 0) else browsePage(parts[i], count, start, extra, options = options)
        }
    }.awaitAll()
    // The window's lists in turn; a list that returned fewer titles than its total promised is skipped once it runs out.
    val cursors = IntArray(parts.size)
    val entries = window.order.mapNotNull { source -> pages[source].entries.getOrNull(cursors[source])?.also { cursors[source]++ } }
    BrowsePage(entries, totals.sum(), pages.flatMap { it.genres }.distinct().sorted(), pages.flatMap { it.letters }.distinct().sorted())
}

internal class MergeWindow(val order: List<Int>, val ranges: List<Pair<Int, Int>>)

/** The lists of the merged positions `offset until offset + limit`, in order, and the run (start, count) of each list's own titles they cover. */
internal fun interleave(totals: List<Int>, offset: Int, limit: Int): MergeWindow {
    val order = mutableListOf<Int>()
    val starts = IntArray(totals.size)
    val counts = IntArray(totals.size)
    val end = offset + limit
    var position = 0
    var round = 0
    while (position < end && totals.any { it > round }) {
        for ((source, total) in totals.withIndex()) {
            if (total <= round || position >= end) continue
            if (position < offset) starts[source]++ else { order += source; counts[source]++ }
            position++
        }
        round++
    }
    return MergeWindow(order, totals.indices.map { starts[it] to counts[it] })
}
