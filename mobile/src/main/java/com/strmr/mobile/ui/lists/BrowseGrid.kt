package com.strmr.mobile.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.PosterCard
import com.strmr.mobile.ui.components.TitleMenu
import com.strmr.mobile.ui.components.rememberCardOpener
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.BrowseSort
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.indexesLetters
import com.strmr.tv.library.TitleActions
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.ui.lists.ListGridModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * A whole list as an adaptive poster grid (the TV `ListGridScreen` and the iPhone list page),
 * paged from the server as the grid nears its end, with the sort, random pick and list
 * actions in the top bar and the filters as chips. [large] gives it a section's collapsing
 * title (the Watchlist tab). Pull down to re-read it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseGridPage(list: BrowseList, query: Map<String, String>, showBack: Boolean, large: Boolean = false) {
    val scope = rememberCoroutineScope()
    val model = remember(list, query) { ListGridModel(list, query, scope) }
    val gridState = rememberLazyGridState()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(model) { model.followItemChanges() }
    LaunchedEffect(model.reloadKey) {
        model.reload()
        gridState.scrollToItem(0)
    }
    // Watched, hidden or set aside from a menu: the loaded part re-reads in place.
    var seenChanges by remember { mutableIntStateOf(TitleActions.changes) }
    LaunchedEffect(TitleActions.changes) {
        if (TitleActions.changes == seenChanges) return@LaunchedEffect
        seenChanges = TitleActions.changes
        model.rereadLoaded()
    }
    // A title added to or removed from the watchlist from its menu.
    if (list == BrowseList.Watchlist) {
        val count = WatchlistStore.items.size
        var seenCount by remember { mutableIntStateOf(count) }
        LaunchedEffect(count) {
            if (count == seenCount) return@LaunchedEffect
            seenCount = count
            model.rereadLoaded()
        }
    }
    NearEnd(gridState) { scope.launch { model.loadPage() } }

    PageScaffold(
        model.title, showBack = showBack, large = large,
        actions = { BrowseActions(model) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    if (model.total == null) model.reload() else model.rereadLoaded()
                    refreshing = false
                }
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            BrowseGrid(model, gridState) { scope.launch { model.reload() } }
        }
    }
}

@Composable
private fun BrowseGrid(model: ListGridModel, gridState: LazyGridState, retry: () -> Unit) {
    val list = model.list
    val cards = model.cards
    val total = model.total
    val open = rememberCardOpener()
    LazyVerticalGrid(
        PosterColumns, state = gridState, contentPadding = gridPadding(top = 0.dp),
        horizontalArrangement = GridSpacing, verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullWidth("header") {
            // The chips and the letters run to the screen's edges.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                total?.let {
                    Text(
                        if (it == 1) "1 title" else "$it titles",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (list.hasChips()) BrowseFilterChips(model, Modifier.bleed(16.dp))
                if (list.indexesLetters && model.options.sort == BrowseSort.NAME && model.letters.isNotEmpty()) {
                    LetterStrip(Modifier.bleed(16.dp), model.letters, model.options.letter) { model.options = model.options.copy(letter = it) }
                }
                model.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        items(cards.size, key = { cards[it].id }, contentType = { "poster" }) { i ->
            GridCard(cards[i], removable = model.manageable, open = open, remove = model::remove)
        }
        when {
            cards.isNotEmpty() && model.loadingMore -> fullWidth("more") { GridSpinner(top = 8.dp) }
            cards.isNotEmpty() -> Unit
            model.loadError != null -> fullWidth("error") {
                GridStatus("Couldn't load this list", model.loadError, Icons.Filled.Warning, "Try Again", retry)
            }
            total != null && (model.options.isFiltered || model.options.letter != null) -> fullWidth("filtered") {
                GridStatus("No matches", "No titles match these filters.", Icons.Filled.FilterAltOff)
            }
            total != null -> fullWidth("empty") {
                GridStatus(
                    "Nothing here yet",
                    if (list == BrowseList.Watchlist) "Add movies and shows from their details page, or long press a poster." else null,
                    Icons.Outlined.Inbox,
                )
            }
            else -> fullWidth("loading") { GridSpinner() }
        }
    }
}

/**
 * A poster; long press shows the title menu, or on the profile's own lists a small menu with
 * Remove from List first.
 */
@Composable
private fun GridCard(card: HomeCard, removable: Boolean, open: (HomeCard) -> Unit, remove: (HomeCard) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        PosterCard(
            card, Dp.Unspecified, onClick = { open(card) },
            onLongClick = { if (removable) menu = true else TitleMenu.show(card) },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Remove from List") },
                leadingIcon = { Icon(Icons.Filled.PlaylistRemove, null) },
                onClick = { menu = false; remove(card) },
            )
            DropdownMenuItem(
                text = { Text("More Actions…") },
                leadingIcon = { Icon(Icons.Filled.MoreHoriz, null) },
                onClick = { menu = false; TitleMenu.show(card) },
            )
        }
    }
}

/** Calls [load] when the grid shows its last half page (about 30 cards from the end). */
@Composable
fun NearEnd(state: LazyGridState, load: () -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            // With the count, so a page that leaves the end still in view asks for the next.
            (info.totalItemsCount > 0 && last >= info.totalItemsCount - 1 - ListGridModel.PAGE / 2) to info.totalItemsCount
        }.distinctUntilChanged().collect { (near, _) -> if (near) load() }
    }
}

/** Widens a full-width grid row past the grid's [edge] padding, so it scrolls edge to edge. */
fun Modifier.bleed(edge: Dp): Modifier = layout { measurable, constraints ->
    val px = edge.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px),
    )
    layout(placeable.width - 2 * px, placeable.height) { placeable.place(-px, 0) }
}
