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
 * movies, shows or both. Each tile opens its whole list as a grid.
 */
@Composable
fun ListsScreen() {
    // null is All: both kinds' rows.
    var kind by rememberSaveable { mutableStateOf<BrowseKind?>(null) }
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
        // All / Movies / Shows as a button opening a picker: a chip row would change the rows below just by moving past it.
        item("kind") {
            OutlinedButton(onClick = { choosingKind = true }, modifier = Modifier.padding(start = ContentMargin)) { Text("${kind?.noun ?: "All"}  ▾") }
        }
        // All shows each kind's genres, then each kind's decades, as the Expo app does.
        val kinds = kind?.let { listOf(it) } ?: BrowseKind.entries
        fun rowTitle(title: String, rowKind: BrowseKind) =
            if (kind != null) title else if (rowKind == BrowseKind.MOVIES) "Movie $title" else "Show $title"
        for (k in kinds) item("genres-$k") { TileRow(rowTitle("Genres", k), k.genres.map { TileSpec(BrowseList.Genre(it.id, it.name, k)) }) }
        for (k in kinds) item("decades-$k") { TileRow(rowTitle("Decades", k), k.decades.map { TileSpec(BrowseList.Decade(it, k)) }) }
    }

    if (choosingKind) {
        val kinds = listOf(null) + BrowseKind.entries
        PickerDialog("Browse", kinds.map { it?.noun ?: "All" }, kinds.indexOf(kind), onPick = { kind = kinds[it] }, onDismiss = { choosingKind = false })
    }
    if (naming) {
        ListNameDialog("New List", "Add titles to it with “Manage Lists…” on any title.", confirm = "Create", onDismiss = { naming = false }) { name ->
            CustomListsStore.create(name)
        }
    }
}
