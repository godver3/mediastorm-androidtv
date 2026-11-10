package com.strmr.mobile.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strmr.tv.api.ApiClient
import com.strmr.tv.home.HomeStore
import com.strmr.tv.library.TitleActions
import com.strmr.tv.library.WatchlistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * One Home view's store and what it last reacted to. Kept by [HomeStores] while the app runs,
 * as the TV shell keeps its Home stores: Home's page leaves composition while another tab or
 * a details page is up, and coming back must neither reload every row nor miss a title action
 * or watchlist change made meanwhile.
 */
internal class HomeEntry(val store: HomeStore) {
    /** [TitleActions.changes] when the rows were last loaded for it. */
    var actions = TitleActions.changes
    /** The watchlist the Watchlist row was last loaded for. */
    var watchlist = WatchlistStore.items
}

/** Home's stores, one per view, for the signed-in profile (a new profile starts over). Main thread only. */
internal object HomeStores {
    private var profileId: String? = null
    private var scope: CoroutineScope = MainScope()
    private val entries = mutableMapOf<String, HomeEntry>()

    fun entry(view: String): HomeEntry {
        val profile = ApiClient.profileId
        if (profile != profileId) {
            scope.cancel()
            scope = MainScope()
            entries.clear()
            profileId = profile
        }
        return entries.getOrPut(view) { HomeEntry(HomeStore(scope, view)) }
    }
}

/** Card widths on Home's rows (the iPhone app's: wider on a tablet). */
internal object HomeMetrics {
    @Composable
    @ReadOnlyComposable
    fun wide(): Boolean = LocalConfiguration.current.smallestScreenWidthDp >= 600

    @Composable
    @ReadOnlyComposable
    fun posterWidth(): Dp = if (wide()) 150.dp else 116.dp

    @Composable
    @ReadOnlyComposable
    fun landscapeWidth(): Dp = if (wide()) 300.dp else 240.dp

    val gutter = 16.dp
}
