package com.strmr.mobile.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.home.BrowseKind
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.StreamingService
import com.strmr.tv.home.decades
import com.strmr.tv.home.genres
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.ListsTabStore
import com.strmr.tv.ui.lists.TileSpec
import com.strmr.tv.ui.lists.hubs
import com.strmr.tv.ui.lists.personalTiles
import com.strmr.tv.ui.lists.seeds
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * The Lists tab (the TV Lists tab, laid out as the iPhone's): For You, the profile's lists,
 * libraries (a scanning one says so), streaming services, connected lists, collection hubs,
 * charts, recommendations, then TMDB genres and decades for movies, shows or both (the top
 * bar's menu). Each tile opens its whole list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(asPage: Boolean) {
    // null is All: both kinds' rows.
    var kind by rememberSaveable { mutableStateOf<BrowseKind?>(null) }
    var naming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        val lists = scope.async { runCatching { CustomListsStore.refresh() } }
        val hidden = scope.async { runCatching { HiddenItemsStore.refresh() } }
        val tab = scope.async { runCatching { ListsTabStore.refresh() } }
        lists.await(); hidden.await(); tab.await()
    }
    // On every visit: lists made or changed elsewhere show up.
    LaunchedEffect(Unit) { refresh() }

    val continueWatching = ListsTabStore.continueWatching
    val loadedShelves = ListsTabStore.shelves
    val shelves = loadedShelves.orEmpty()
    val lists = CustomListsStore.lists
    // The Hidden tile stays for the visit once shown.
    val hiddenCount = HiddenItemsStore.items.size
    var hiddenShown by remember { mutableStateOf(false) }
    LaunchedEffect(hiddenCount) { if (hiddenCount > 0) hiddenShown = true }

    PageScaffold(
        "Lists", showBack = asPage,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    (listOf(null) + BrowseKind.entries).forEach { k ->
                        DropdownMenuItem(
                            text = { Text(k?.noun ?: "Movies and Shows") },
                            leadingIcon = { if (k == kind) Icon(Icons.Filled.Check, null) },
                            onClick = { menu = false; kind = k },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("New List…") }, leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { menu = false; naming = true },
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; scope.launch { refresh(); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item("for-you") { TileRow("For You", personalTiles(continueWatching, lists?.firstOrNull { it.isSetAside }, hiddenCount, hiddenShown)) }
                if (lists != null) item("yours") {
                    TileRow("Your Lists", lists.filterNot { it.isSetAside }.map { TileSpec(BrowseList.Custom(it.id, it.name), count = it.itemCount) }) {
                        naming = true
                    }
                }
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
                item("connected") { TileRow("Connected Lists", remote.map { TileSpec(it) }) }
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
                // All shows each kind's genres, then each kind's decades, as the Expo app does.
                val kinds = kind?.let { listOf(it) } ?: BrowseKind.entries
                fun rowTitle(title: String, rowKind: BrowseKind) =
                    if (kind != null) title else if (rowKind == BrowseKind.MOVIES) "Movie $title" else "Show $title"
                for (k in kinds) item("genres-$k") { TileRow(rowTitle("Genres", k), k.genres.map { TileSpec(BrowseList.Genre(it.id, it.name, k)) }) }
                for (k in kinds) item("decades-$k") { TileRow(rowTitle("Decades", k), k.decades.map { TileSpec(BrowseList.Decade(it, k)) }) }
            }
        }
    }

    if (naming) {
        ListNameDialog("New List", "Add titles to it with “Manage Lists…” on any title.", confirm = "Create", onDismiss = { naming = false }) { name ->
            CustomListsStore.create(name)
        }
    }
}
