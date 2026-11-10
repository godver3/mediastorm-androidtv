package com.strmr.tv.ui.lists

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.home.BrowseEntry
import com.strmr.tv.home.BrowseKind
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.ShelfConfig
import com.strmr.tv.home.ShelfPhase
import com.strmr.tv.home.browsePage
import com.strmr.tv.library.WatchlistStore

// The Lists tab's rows as data, shared by the TV and phone Lists tabs.

/** Artwork of a list's first titles. */
data class TileArt(val posters: List<String> = emptyList(), val backdrops: List<String> = emptyList())

/**
 * A tile to show: its list, its size when known up front, and artwork when the tab already
 * has it (otherwise the tile fetches the list's first titles). `title` overrides the list's
 * name (collection hub cards); `logo` draws on a plain panel instead (services, hub cards).
 */
data class TileSpec(
    val list: BrowseList,
    val title: String? = null,
    val count: Int? = null,
    val art: TileArt? = null,
    val logo: String? = null,
    /** Under the name when there is no count (a library's source and kind). */
    val subtitle: String? = null,
)

/** Continue Watching (when there is any), Set Aside, Watchlist, Permanent Prequeue, Hidden (when there is any, or was this visit). */
fun personalTiles(
    continueWatching: List<ContinueWatchingItem>?,
    setAside: com.strmr.tv.home.CustomList?,
    hidden: Int,
    hiddenShown: Boolean,
): List<TileSpec> = buildList {
    if (!continueWatching.isNullOrEmpty()) {
        val first = continueWatching.take(4)
        add(TileSpec(BrowseList.ContinueWatching, count = continueWatching.size,
            art = TileArt(first.mapNotNull { it.textPosterUrl ?: it.posterUrl }, first.mapNotNull { it.backdropUrl })))
    }
    setAside?.let { add(TileSpec(BrowseList.Custom(it.id, it.name), count = it.itemCount)) }
    add(TileSpec(BrowseList.Watchlist, count = if (WatchlistStore.isLoaded) WatchlistStore.items.size else null))
    add(TileSpec(BrowseList.PermanentPrequeue))
    // Titles hidden from this profile, where a long press unhides them.
    if (hidden > 0 || hiddenShown) add(TileSpec(BrowseList.Hidden, count = hidden))
}

/** Collection hub shelves: each card opens another shelf's list, with the card's own art. */
fun hubs(shelves: List<ShelfConfig>): List<Pair<String, List<TileSpec>>> {
    val byId = shelves.associateBy { it.id }
    return shelves.filter { it.type == "collection-hub" }.mapNotNull { hub ->
        val tiles = hub.collectionItems.orEmpty().filter { it.enabled != false }.sortedBy { it.order ?: 0 }.mapNotNull { item ->
            val list = item.sourceShelfId?.let { byId[it] }?.browseList ?: return@mapNotNull null
            val art = item.heroArtUrl?.let { TileArt(backdrops = listOf(it)) }
            TileSpec(list, title = item.name, art = art, logo = if (art == null) item.logoUrl else null)
        }
        if (tiles.isEmpty()) null else hub.title to tiles
    }
}

/** "Because You Watched": up to three movies and three shows from Continue Watching, alternating. */
fun seeds(items: List<ContinueWatchingItem>): List<BrowseList> {
    val seen = mutableSetOf<String>()
    val candidates = items.mapNotNull { item ->
        val tmdb = item.externalIds?.get("tmdb")?.takeIf { it.isNotEmpty() && seen.add(it) } ?: return@mapNotNull null
        BrowseList.Similar(tmdb, if (item.isMovie) BrowseKind.MOVIES else BrowseKind.SHOWS, item.seriesTitle, item.backdropUrl)
    }
    val movies = candidates.filter { it.kind == BrowseKind.MOVIES }.take(3)
    val shows = candidates.filter { it.kind == BrowseKind.SHOWS }.take(3)
    return (0 until maxOf(movies.size, shows.size)).flatMap { listOfNotNull(movies.getOrNull(it), shows.getOrNull(it)) }
}

/**
 * The first few titles of each list, for its tile, kept per profile for the app's lifetime
 * so scrolling back and forth or switching Movies / Shows doesn't refetch.
 */
object BrowsePreviews {
    private val cache = mutableMapOf<String, TileArt>()

    suspend fun art(list: BrowseList, version: Int?): TileArt {
        if (list is BrowseList.Similar) return TileArt() // the seed's own backdrop
        val key = "${ApiClient.profileId}|$list|${version ?: ""}"
        cache[key]?.let { return it }
        // Four posters: the quick pass, not a whole remote list read and filled in first.
        val page = runCatching { ApiClient.browsePage(list, limit = 4, phase = ShelfPhase.CARDS) }.getOrNull() ?: return TileArt()
        val art = TileArt(page.entries.mapNotNull(::posterOf), page.entries.mapNotNull(::backdropOf))
        cache[key] = art
        return art
    }

    private fun posterOf(e: BrowseEntry): String? = when (e) {
        is BrowseEntry.TitleEntry -> (e.title.textPoster ?: e.title.poster)?.url
        is BrowseEntry.ItemEntry -> e.item.textPosterUrl ?: e.item.posterUrl
        is BrowseEntry.WatchingEntry -> e.item.textPosterUrl ?: e.item.posterUrl
        is BrowseEntry.LibraryItemEntry -> e.entry.group.posterUrl
    }

    private fun backdropOf(e: BrowseEntry): String? = when (e) {
        is BrowseEntry.TitleEntry -> e.title.backdrop?.url
        is BrowseEntry.ItemEntry -> e.item.backdropUrl
        is BrowseEntry.WatchingEntry -> e.item.backdropUrl
        is BrowseEntry.LibraryItemEntry -> e.entry.group.backdropUrl
    }
}
