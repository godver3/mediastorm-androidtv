package com.strmr.tv.ui.lists

import com.strmr.tv.api.Profile
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.CustomList
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.ListsTabStore
import com.strmr.tv.library.ProfileDataContext
import com.strmr.tv.library.WatchlistStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Existing profile datasets, presented as stable navigation choices. */
object PersonalArea {
    val loved = BrowseList.Remote("personal-loved", "Loved Titles", mapOf("source" to "loved"))

    // The new aggregate does not widen child access through older list APIs.
    fun available(profile: Profile?) = profile != null && profile.isKidsProfile != true

    fun tiles(continueWatching: List<ContinueWatchingItem>?, watchlistCount: Int?, preferenceRevision: Long): List<TileSpec> = listOf(
        TileSpec(BrowseList.ContinueWatching, count = continueWatching?.size,
            art = continueWatching?.take(4)?.let { first -> TileArt(first.mapNotNull { it.textPosterUrl ?: it.posterUrl }, first.mapNotNull { it.backdropUrl }) }),
        TileSpec(BrowseList.Watchlist, title = "My List", count = watchlistCount),
        TileSpec(loved, previewRevision = preferenceRevision),
        // Recommendations run only after selection, never to decorate this entry.
        TileSpec(BrowseList.Personalized, title = "For You", art = TileArt()),
    )

    fun ownLists(lists: List<CustomList>?): List<TileSpec> = lists.orEmpty()
        .filterNot { it.isSetAside }.distinctBy { it.id }
        .map { TileSpec(BrowseList.Custom(it.id, it.name), count = it.itemCount) }
}

/** Profile-local reads only; existing stores share and isolate their requests. */
suspend fun refreshPersonalArea() {
    val context = ProfileDataContext.current
    coroutineScope {
        launch { ProfileDataContext.requireCurrent(context); WatchlistStore.refresh() }
        launch { ProfileDataContext.requireCurrent(context); CustomListsStore.refresh() }
        launch { ProfileDataContext.requireCurrent(context); ListsTabStore.refresh() }
    }
    ProfileDataContext.requireCurrent(context)
}
