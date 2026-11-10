package com.strmr.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.TitleMenu
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.app.AppSession
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.CardText
import com.strmr.tv.home.CarouselPick
import com.strmr.tv.home.HomeLayoutStore
import com.strmr.tv.home.HomeSlot
import com.strmr.tv.home.HomeStore
import com.strmr.tv.home.HomeViewStore
import com.strmr.tv.home.HomeViews
import com.strmr.tv.home.ShelfStyle
import com.strmr.tv.library.TitleActions
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.player.PlaybackCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home on a phone or tablet, as the iPhone app's: the chosen Home view's rows from the shared
 * [HomeStore], each scrolling sideways, poster or 16:9 as the row's style says, under the Top
 * Carousel (Settings › Home Layout). The top bar's menu switches Home views (Home, Movies,
 * Shows and the profile's pages). Pull to refresh reloads every row.
 */
@Composable
fun HomeScreen() {
    val view = HomeViewStore.current
    val entry = HomeStores.entry(view)
    PageScaffold(HomeViewStore.label(view), showBack = false, actions = { HomeViewPicker() }) { padding ->
        // A new view starts at the top with its own rows.
        key(view) { HomeContent(entry, padding) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(entry: HomeEntry, padding: PaddingValues) {
    val store = entry.store
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    val list = rememberLazyListState()
    val layout = HomeLayoutStore
    val sessionsEnded by PlaybackCoordinator.sessionsEnded.collectAsState()
    val wakes by AppSession.wakes.collectAsState()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (store.settings == null) {
            store.ensureLoaded()
        } else {
            store.publishLayout()
            store.refreshIfStale()
        }
    }
    LaunchedEffect(sessionsEnded) { if (sessionsEnded > 0) store.reloadPlaybackRows() }
    LaunchedEffect(wakes) { if (wakes > 0) store.refreshIfStale() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60_000L)
            store.refreshIfStale()
        }
    }
    // A title action anywhere (Set Aside, hide, watched): every row reloads, since any row's
    // cards carry the server's watch badge. Rows keep their cards until the new ones arrive.
    val actions = TitleActions.changes
    LaunchedEffect(actions) {
        if (actions == entry.actions) return@LaunchedEffect
        entry.actions = actions
        store.load()
    }
    // A title added to or removed from the watchlist: the Watchlist row follows.
    val watchlist = WatchlistStore.items
    LaunchedEffect(watchlist) {
        if (watchlist == entry.watchlist) return@LaunchedEffect
        entry.watchlist = watchlist
        store.reloadWatchlistRow()
    }
    // The tab selected again at its root: back to the top.
    val reselected = navigator.reselected
    val shownAt = remember { reselected }
    LaunchedEffect(reselected) { if (reselected != shownAt) list.animateScrollToItem(0) }

    val onCard = rememberHomeCardOpener { store.settings }
    val slots = store.slots
    val carouselId = if (layout.topCarousel) CarouselPick.pick(slots, layout.carouselRow) else null
    val carouselShelf = (slots.firstOrNull { it.id == carouselId } as? HomeSlot.Shelf)?.shelf
    val slides = remember(carouselShelf) { carouselShelf?.let(CarouselPick::slides).orEmpty() }
    val showTitles = layout.cardText != CardText.NONE

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try { store.load() } finally { refreshing = false }
            }
        },
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            if (slides.isNotEmpty()) {
                item(key = "__carousel__") {
                    HomeCarousel(slides, onCard = onCard, onMenu = { TitleMenu.show(it) })
                }
            }
            items(slots.filterNot { it.id == carouselId || it is HomeSlot.Failed }, key = { it.id }) { slot ->
                when (slot) {
                    is HomeSlot.Shelf -> ShelfRow(
                        slot.shelf,
                        landscape = layout.style(slot.id) == ShelfStyle.LANDSCAPE,
                        showTitles = showTitles,
                        onCard = onCard,
                        onMenu = { TitleMenu.show(it, continueWatching = slot.shelf.list == BrowseList.ContinueWatching) },
                        onExplore = { navigator.openList(it.list, store.settings?.viewQuery) },
                    )
                    is HomeSlot.Loading -> PlaceholderRow(slot.title)
                    is HomeSlot.Failed -> Unit
                }
            }
            if (slots.all { it is HomeSlot.Failed }) item(key = "__status__") { HomeStatus(store) { scope.launch { store.load() } } }
        }
    }
}

/** Loading, a load error (with Try Again) or no rows, where the rows would be. */
@Composable
private fun HomeStatus(store: HomeStore, onRetry: () -> Unit) {
    val error = store.loadError
    when {
        error != null -> StatusMessage(Icons.Outlined.CloudOff, "Couldn't Load Home", error) {
            FilledTonalButton(onClick = onRetry) { Text("Try Again") }
        }
        store.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> StatusMessage(
            Icons.Outlined.Home,
            if (store.view == HomeViews.ALL) "Nothing on Home" else "Nothing on This Page",
            if (store.view == HomeViews.ALL) "Turn on rows for this profile in the server's settings."
            else "Add rows to this page in the server's settings.",
        )
    }
}

/** A centred symbol, title and line (Material's empty state), with an optional action under it. */
@Composable
internal fun StatusMessage(icon: ImageVector, title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}
