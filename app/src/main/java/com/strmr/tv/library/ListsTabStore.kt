package com.strmr.tv.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.home.ShelfConfig
import com.strmr.tv.home.continueWatching
import com.strmr.tv.home.homeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * What the Lists tab builds its profile rows from: the Home shelf config (streaming services,
 * connected lists, collection hubs) and Continue Watching. Kept here, loaded with the shell,
 * so the tab draws them on every open instead of swapping them in after its own requests.
 * Main thread only.
 */
object ListsTabStore {
    /** null until the first load. */
    var shelves by mutableStateOf<List<ShelfConfig>?>(null)
        private set
    /** null until the first load. */
    var continueWatching by mutableStateOf<List<ContinueWatchingItem>?>(null)
        private set
    private var refreshing: CompletableDeferred<Unit>? = null

    /** Loads both; concurrent callers share one request, and a failure keeps what was shown. */
    suspend fun refresh() {
        refreshing?.let { return it.await() }
        val done = CompletableDeferred<Unit>()
        refreshing = done
        val profile = ApiClient.profileId
        try {
            coroutineScope {
                val loadedShelves = async { runCatching { ApiClient.homeSettings().shelves }.getOrNull() }
                val loadedItems = async { runCatching { ApiClient.continueWatching() }.getOrNull() }
                val newShelves = loadedShelves.await()
                val newItems = loadedItems.await()
                // A profile switch mid-load: the old profile's rows mustn't land on the new one.
                if (profile != ApiClient.profileId) return@coroutineScope
                newShelves?.let { shelves = it }
                newItems?.let { continueWatching = it }
            }
        } finally {
            refreshing = null
            done.complete(Unit)
        }
    }

    /** Forgets everything (profile switch). */
    fun reset() {
        shelves = null
        continueWatching = null
    }
}
