package com.strmr.tv.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.home.hiddenFromContinueWatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Settings › Hidden Items: the titles hidden from Continue Watching and those hidden from this
 * profile, each with its undo. Continue Watching's list is loaded here; the profile's is
 * [HiddenItemsStore]'s, so an Unhide here reaches every screen. Both undos go through
 * [TitleActions] (its toast, and its change count, which reloads Home's Continue Watching).
 * One per screen; main thread only.
 */
class HiddenItemsSettings {
    sealed interface Load {
        data object Loading : Load
        data object Loaded : Load
        data class Failed(val message: String) : Load
    }

    var continueLoad by mutableStateOf<Load>(Load.Loading)
        private set
    var continueItems by mutableStateOf<List<HiddenItem>>(emptyList())
        private set
    var profileLoad by mutableStateOf<Load>(Load.Loading)
        private set
    val profileItems: List<HiddenItem> get() = HiddenItemsStore.items

    /** Keys ([key]) of rows whose undo is in flight, so a second press does nothing. */
    var busy by mutableStateOf<Set<String>>(emptySet())
        private set

    val isLoading: Boolean get() = continueLoad == Load.Loading || profileLoad == Load.Loading

    suspend fun load() = coroutineScope {
        launch { loadContinueWatching() }
        launch { loadProfile() }
    }

    suspend fun loadContinueWatching() {
        continueLoad = Load.Loading
        continueLoad = try {
            continueItems = ApiClient.hiddenFromContinueWatching()
            Load.Loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("HiddenItems", "continue watching list failed: ${e.message}")
            Load.Failed(e.message ?: "Couldn't load")
        }
    }

    suspend fun loadProfile() {
        profileLoad = Load.Loading
        HiddenItemsStore.refresh()
        profileLoad = if (HiddenItemsStore.isLoaded) Load.Loaded else Load.Failed("Couldn't load")
    }

    /** Shows the title in Continue Watching again; true when it left this list. */
    suspend fun showInContinueWatching(item: HiddenItem): Boolean = act(key(Section.ContinueWatching, item)) {
        TitleActions.showInContinueWatching(item.toTarget()).also { done ->
            if (done) continueItems = continueItems.filterNot { it.id == item.id && it.mediaType == item.mediaType }
        }
    }

    /** Unhides the title from this profile; true when it left this list. */
    suspend fun unhide(item: HiddenItem): Boolean = act(key(Section.Profile, item)) {
        TitleActions.unhide(item.toTarget())
    }

    private suspend fun act(key: String, action: suspend () -> Boolean): Boolean {
        if (key in busy) return false
        busy = busy + key
        return try { action() } finally { busy = busy - key }
    }

    enum class Section(val prefix: String) { ContinueWatching("cw"), Profile("hp") }

    companion object {
        /** A row's stable key within its section (for lazy lists and focus). */
        fun key(section: Section, item: HiddenItem) = "${section.prefix}:${item.mediaType}:${item.id}"

        /** The section's placeholder row (loading, empty or failed). */
        fun statusKey(section: Section) = "${section.prefix}:status"

        /**
         * Where focus goes after the row at [removedIndex] left a list now [remaining] long:
         * the row that took its place, else the one above; null when the list is empty.
         */
        fun focusIndexAfterRemoval(removedIndex: Int, remaining: Int): Int? =
            if (remaining <= 0) null else removedIndex.coerceIn(0, remaining - 1)
    }
}
