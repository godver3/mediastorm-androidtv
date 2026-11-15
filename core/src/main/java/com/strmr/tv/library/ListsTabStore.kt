package com.strmr.tv.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.LibraryInfo
import com.strmr.tv.api.libraries
import com.strmr.tv.home.ShelfConfig
import com.strmr.tv.home.continueWatching
import com.strmr.tv.home.homeSettings
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
    /** The local, Plex and Jellyfin libraries the profile may browse (empty until loaded). */
    var libraries by mutableStateOf<List<LibraryInfo>>(emptyList())
        private set
    private val refreshes = ProfileRefresh()

    suspend fun refresh() = refresh {
        coroutineScope {
            val loadedShelves = async { optionalProfileRead { ApiClient.homeSettings().shelves } }
            val loadedItems = async { optionalProfileRead { ApiClient.continueWatching() } }
            val loadedLibraries = async { optionalProfileRead { ApiClient.libraries() } }
            ListsTabSnapshot(loadedShelves.await(), loadedItems.await(), loadedLibraries.await())
        }
    }

    internal suspend fun refresh(load: suspend () -> ListsTabSnapshot) {
        refreshes.run(load, success = { snapshot ->
            snapshot.shelves?.let { shelves = it }
            snapshot.continueWatching?.let { continueWatching = it }
            snapshot.libraries?.let { libraries = it }
        })
    }

    /** Forgets everything (profile switch). */
    fun reset() {
        refreshes.reset()
        shelves = null
        continueWatching = null
        libraries = emptyList()
    }
}

internal data class ListsTabSnapshot(
    val shelves: List<ShelfConfig>? = null,
    val continueWatching: List<ContinueWatchingItem>? = null,
    val libraries: List<LibraryInfo>? = null,
)
