package com.strmr.mobile.downloads

/** The Downloads list's order (pure, for tests). */
object DownloadSorting {
    private fun key(item: DownloadItem) = item.name.lowercase() + "%04d%04d".format(item.seasonNumber ?: 0, item.episodeNumber ?: 0)

    fun sorted(items: List<DownloadItem>, sort: DownloadSettings.Sort): List<DownloadItem> = when (sort) {
        DownloadSettings.Sort.NEWEST -> items.sortedByDescending { it.createdAt }
        DownloadSettings.Sort.OLDEST -> items.sortedBy { it.createdAt }
        DownloadSettings.Sort.NAME_ASCENDING -> items.sortedBy(::key)
        DownloadSettings.Sort.NAME_DESCENDING -> items.sortedByDescending(::key)
    }

    /** Finished episodes by show, shows in the sort (a show sorts by its newest episode). */
    fun shows(items: List<DownloadItem>, sort: DownloadSettings.Sort): List<Pair<String, List<DownloadItem>>> {
        val groups = items.filter { it.isCompleted && it.isEpisode }.groupBy { it.showKey }
        val latest = groups.values.mapNotNull { eps -> eps.maxByOrNull { it.createdAt } }
        return sorted(latest, sort).mapNotNull { l -> groups[l.showKey]?.let { l.showKey to it } }
    }

    /** One show's episodes by season, in episode order. */
    fun seasons(episodes: List<DownloadItem>): List<Pair<Int, List<DownloadItem>>> =
        episodes.groupBy { it.seasonNumber ?: 0 }.toSortedMap()
            .map { (season, eps) -> season to eps.sortedBy { it.episodeNumber ?: 0 } }
}
