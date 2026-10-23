package com.strmr.tv.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.home.BrowseKind
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.ShelfConfig
import com.strmr.tv.home.StreamingService
import com.strmr.tv.home.decades
import com.strmr.tv.home.genres
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.ListsTabStore
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.ui.ContentMargin
import com.strmr.tv.ui.PickerDialog

/**
 * The Lists tab: the profile's own titles (Continue Watching, Set Aside, Watchlist, Permanent
 * Prequeue), its custom lists, streaming services, connected lists and collection hubs from
 * the shelf config, the server's charts, recommendations, then TMDB genres and decades for
 * movies or shows. Each tile opens its whole list as a grid.
 */
@Composable
fun ListsScreen() {
    var kind by rememberSaveable { mutableStateOf(BrowseKind.MOVIES) }
    var naming by remember { mutableStateOf(false) }
    var choosingKind by remember { mutableStateOf(false) }
    // On every visit: lists made or changed elsewhere show up. Everything here is loaded with
    // the shell, so the rows draw at once and only update in place.
    LaunchedEffect(Unit) { CustomListsStore.refresh() }
    LaunchedEffect(Unit) { HiddenItemsStore.refresh() }
    LaunchedEffect(Unit) { ListsTabStore.refresh() }
    val continueWatching = ListsTabStore.continueWatching
    val loadedShelves = ListsTabStore.shelves
    val shelves = loadedShelves.orEmpty()

    val lists = CustomListsStore.lists
    // The Hidden tile stays for the visit once shown: unhiding the last title from its grid
    // mustn't drop the tile focus returns to on Back (focus would fall to the tabs, where
    // the next Back leaves the app).
    val hiddenCount = HiddenItemsStore.items.size
    var hiddenShown by remember { mutableStateOf(false) }
    LaunchedEffect(hiddenCount) { if (hiddenCount > 0) hiddenShown = true }
    LazyColumn(
        Modifier.fillMaxSize().padding(top = 76.dp).clipToBounds(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item("for-you") { TileRow("For You", personalTiles(continueWatching, lists?.firstOrNull { it.isSetAside }, hiddenCount, hiddenShown)) }
        if (lists != null) item("yours") {
            TileRow("Your Lists", lists.filterNot { it.isSetAside }.map { TileSpec(BrowseList.Custom(it.id, it.name), count = it.itemCount) }) {
                naming = true
            }
        }
        // The profile's local, Plex and Jellyfin libraries, each opening its titles.
        val libraries = ListsTabStore.libraries
        if (libraries.isNotEmpty()) item("libraries") {
            TileRow("Libraries", libraries.map { TileSpec(BrowseList.Library(it.ref), subtitle = it.tileSubtitle) })
        }
        // Rows from the shelf config wait for it, rather than showing the default services and then swapping them.
        if (loadedShelves != null) item("services") {
            val services = StreamingService.resolved(shelves.firstOrNull { it.id == "streaming-services" }?.streamingServices)
            TileRow("Streaming Services", services.map { TileSpec(BrowseList.Service(it), logo = it.logoUrl) })
        }
        val remote = shelves.filter { it.remoteQuery != null }.mapNotNull { it.browseList }
        if (remote.isNotEmpty()) item("connected") { TileRow("Connected Lists", remote.map { TileSpec(it) }) }
        for ((title, tiles) in hubs(shelves)) item("hub-$title") { TileRow(title, tiles) }
        item("charts") {
            TileRow("Charts", listOf(
                BrowseList.TopTen(), BrowseList.TopTen(BrowseKind.MOVIES), BrowseList.TopTen(BrowseKind.SHOWS),
                BrowseList.Trending(BrowseKind.MOVIES), BrowseList.Trending(BrowseKind.SHOWS), BrowseList.PopularOnServer,
            ).map { TileSpec(it) })
        }
        item("recommended") {
            TileRow("Recommended", (listOf(BrowseList.Personalized) + seeds(continueWatching.orEmpty())).map { TileSpec(it) })
        }
        // Movies / Shows as a button opening a picker: a chip row would change the rows below just by moving past it.
        item("kind") {
            OutlinedButton(onClick = { choosingKind = true }, modifier = Modifier.padding(start = ContentMargin)) { Text("${kind.noun}  ▾") }
        }
        item("genres-$kind") { TileRow("Genres", kind.genres.map { TileSpec(BrowseList.Genre(it.id, it.name, kind)) }) }
        item("decades-$kind") { TileRow("Decades", kind.decades.map { TileSpec(BrowseList.Decade(it, kind)) }) }
    }

    if (choosingKind) {
        PickerDialog("Browse", BrowseKind.entries.map { it.noun }, kind.ordinal, onPick = { kind = BrowseKind.entries[it] }, onDismiss = { choosingKind = false })
    }
    if (naming) {
        ListNameDialog("New List", "Add titles to it with “Add to List…” on any title.", confirm = "Create", onDismiss = { naming = false }) { name ->
            CustomListsStore.create(name)
        }
    }
}

/** Continue Watching (when there is any), Set Aside, Watchlist, Permanent Prequeue, Hidden (when there is any, or was this visit). */
private fun personalTiles(
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
private fun hubs(shelves: List<ShelfConfig>): List<Pair<String, List<TileSpec>>> {
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
private fun seeds(items: List<ContinueWatchingItem>): List<BrowseList> {
    val seen = mutableSetOf<String>()
    val candidates = items.mapNotNull { item ->
        val tmdb = item.externalIds?.get("tmdb")?.takeIf { it.isNotEmpty() && seen.add(it) } ?: return@mapNotNull null
        BrowseList.Similar(tmdb, if (item.isMovie) BrowseKind.MOVIES else BrowseKind.SHOWS, item.seriesTitle, item.backdropUrl)
    }
    val movies = candidates.filter { it.kind == BrowseKind.MOVIES }.take(3)
    val shows = candidates.filter { it.kind == BrowseKind.SHOWS }.take(3)
    return (0 until maxOf(movies.size, shows.size)).flatMap { listOfNotNull(movies.getOrNull(it), shows.getOrNull(it)) }
}
