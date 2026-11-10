package com.strmr.mobile.ui.watchlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.strmr.mobile.ui.lists.BrowseGridPage
import com.strmr.tv.home.BrowseList
import com.strmr.tv.library.HiddenItemsStore

/**
 * The Watchlist tab, as the iPhone app has it: the watchlist as a list page, paged from the
 * server with its sorts, Movies / Shows, watch-status and genre filters, the A–Z index under a
 * Name sort and a random pick. Long press a poster for the title menu (Remove from Watchlist
 * among it); titles hidden from the profile stay out.
 */
@Composable
fun WatchlistScreen(asPage: Boolean) {
    LaunchedEffect(Unit) { HiddenItemsStore.ensureLoaded() }
    BrowseGridPage(BrowseList.Watchlist, emptyMap(), showBack = asPage, large = !asPage)
}
