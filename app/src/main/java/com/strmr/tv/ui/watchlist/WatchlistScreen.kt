package com.strmr.tv.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.WatchlistItem
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.TitleActions
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.PosterGrid
import com.strmr.tv.ui.fullWidth

private enum class Filter(val label: String) {
    ALL("All"), MOVIES("Movies"), SHOWS("Shows");

    fun includes(item: WatchlistItem) = when (this) {
        ALL -> true
        MOVIES -> item.isMovie
        SHOWS -> !item.isMovie
    }
}

/**
 * The Watchlist tab: the profile's watchlist as posters, most recently added first, with an
 * All / Movies / Shows filter. Long press a poster to remove it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WatchlistScreen() {
    val store = WatchlistStore
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    LaunchedEffect(Unit) { store.refresh() }
    LaunchedEffect(Unit) { HiddenItemsStore.ensureLoaded() }
    // Watched state changed from a menu: the badges re-read.
    val actions = TitleActions.changes
    LaunchedEffect(actions) { if (actions > 0) store.refresh() }
    // Titles hidden from this profile stay out, as on the Expo watchlist.
    val visible = store.items.filter(filter::includes).map(HomeCard::from)
        .filter { it.target?.let(HiddenItemsStore::isHidden) != true }

    // Removing the last title from its menu leaves no card to focus, and focus would fall to the
    // tabs (where Back leaves the app): the selected filter takes it instead. `gridFocused`
    // stays set when the grid loses focus because it emptied.
    val chip = remember { FocusRequester() }
    var gridFocused by remember { mutableStateOf(false) }
    val nowVisible by rememberUpdatedState(visible)
    LaunchedEffect(visible.isEmpty()) {
        if (visible.isNotEmpty() || !gridFocused) return@LaunchedEffect
        withFrameNanos { }
        runCatching { chip.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(top = 76.dp).clipToBounds()) {
        PosterGrid(
            visible,
            modifier = Modifier.onFocusChanged { if (it.hasFocus || nowVisible.isNotEmpty()) gridFocused = it.hasFocus },
            contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 32.dp),
            header = {
                fullWidth("filter") {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp).focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    ) {
                        Filter.entries.forEach { f ->
                            FilterChip(
                                selected = filter == f, onClick = { filter = f },
                                modifier = if (filter == f) Modifier.focusRequester(chip) else Modifier,
                            ) { Text(f.label) }
                        }
                    }
                }
                when {
                    !store.isLoaded -> fullWidth("loading") {
                        Box(Modifier.padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    }
                    store.loadError != null -> fullWidth("error") {
                        Text(store.loadError!!, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 60.dp))
                    }
                    visible.isEmpty() -> fullWidth("empty") {
                        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(AppIcons.BookmarkBorder, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                            Text(
                                if (store.items.isEmpty()) "Your watchlist is empty. Add movies and shows from their details page, or long press a poster."
                                else "Nothing here for this filter.",
                                color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 460.dp).padding(top = 12.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}
