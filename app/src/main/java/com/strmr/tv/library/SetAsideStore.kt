package com.strmr.tv.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.app.AppLog
import com.strmr.tv.home.WatchlistItem
import com.strmr.tv.home.customListItems

/**
 * Set Aside (the Expo app's SetAsideContext): the reserved custom list of titles parked out
 * of Continue Watching, made on first use. Continue Watching already leaves its titles out
 * ([com.strmr.tv.home.continueWatching]). Main thread only.
 */
object SetAsideStore {
    const val NAME = "Set Aside"

    /** The list's items; empty until loaded or when there is no list yet. */
    var items by mutableStateOf<List<WatchlistItem>>(emptyList())
        private set

    /** Re-reads the lists first (as the Expo app's ensureList), so a list deleted or made elsewhere is seen. */
    private suspend fun listId(create: Boolean): String? {
        CustomListsStore.refresh()
        CustomListsStore.lists?.firstOrNull { it.isSetAside }?.let { return it.id }
        if (!create) return null
        AppLog.i("SetAside", "creating the Set Aside list")
        return CustomListsStore.create(NAME).id
    }

    suspend fun refresh() {
        val id = listId(create = false)
        items = if (id == null) emptyList() else runCatching { ApiClient.customListItems(id) }.getOrDefault(items)
    }

    fun reset() { items = emptyList() }

    fun contains(target: DetailsTarget): Boolean = items.any { WatchlistStore.matches(it, target) }

    suspend fun add(target: DetailsTarget) {
        val id = listId(create = true) ?: return
        CustomListsStore.add(target, id)
        refresh()
        AppLog.i("SetAside", "set aside ${target.titleId} (${target.name})")
    }

    suspend fun remove(target: DetailsTarget) {
        val id = listId(create = false) ?: return
        if (items.isEmpty()) refresh()
        for (item in items.filter { WatchlistStore.matches(it, target) }) CustomListsStore.remove(item, id)
        refresh()
        AppLog.i("SetAside", "removed ${target.titleId} (${target.name}) from Set Aside")
    }
}
