package com.strmr.tv.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.CustomList
import com.strmr.tv.home.WatchlistItem
import com.strmr.tv.home.customListItems
import com.strmr.tv.home.customLists
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The profile's custom lists, shared by the Lists tab, the list grids and every "Add to
 * List…" action so a change in one shows everywhere. Main thread only.
 */
object CustomListsStore {
    /** In the server's order; null until the first load. */
    var lists by mutableStateOf<List<CustomList>?>(null)
        private set
    /** The title whose "Add to List" dialog is up (set from any card or details page). */
    var picking by mutableStateOf<DetailsTarget?>(null)
    /**
     * One list's item change: an add ([removed] null; that list's open grids re-read it) or a
     * removal of [removed] (they drop it in place). Only the changed list's grids react, so
     * the others keep their cards and focus.
     */
    data class ItemChange(val listId: String, val removed: WatchlistItem? = null)
    private val _itemChanges = MutableSharedFlow<ItemChange>(extraBufferCapacity = 16)
    val itemChanges: SharedFlow<ItemChange> = _itemChanges
    private var refreshing: CompletableDeferred<Unit>? = null

    /** The lists a title can be added to: all but the reserved Set Aside list, which only Continue Watching actions manage. */
    val userLists: List<CustomList> get() = lists.orEmpty().filterNot { it.isSetAside }

    /** Loads the lists; concurrent callers share one request, and a failure keeps what was shown. */
    suspend fun refresh() {
        refreshing?.let { return it.await() }
        val done = CompletableDeferred<Unit>()
        refreshing = done
        try {
            runCatching { ApiClient.customLists() }.getOrNull()?.let { lists = it }
        } finally {
            refreshing = null
            done.complete(Unit)
        }
    }

    /** Forgets everything (profile switch). */
    fun reset() {
        lists = null
        picking = null
    }

    /** The stored item matching the title in each list that has it, by list ID; lists that couldn't be read are left out. */
    suspend fun memberships(target: DetailsTarget): Map<String, WatchlistItem> = coroutineScope {
        userLists.filter { (it.itemCount ?: 1) > 0 }.map { list ->
            async {
                runCatching { ApiClient.customListItems(list.id) }.getOrNull()
                    ?.firstOrNull { WatchlistStore.matches(it, target) }?.let { list.id to it }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    suspend fun create(name: String): CustomList {
        val list = ApiClient.send<CustomList>("POST", "${ApiClient.profilePath}/custom-lists", body = buildJsonObject { put("name", name) })
        refresh()
        return list
    }

    suspend fun rename(id: String, name: String) {
        ApiClient.sendRaw("PATCH", "${ApiClient.profilePath}/custom-lists/${ApiClient.segment(id)}", body = buildJsonObject { put("name", name) })
        refresh()
    }

    suspend fun delete(id: String) {
        ApiClient.sendRaw("DELETE", "${ApiClient.profilePath}/custom-lists/${ApiClient.segment(id)}")
        lists = lists?.filterNot { it.id == id }
        refresh()
    }

    suspend fun add(target: DetailsTarget, listId: String) {
        ApiClient.sendRaw("POST", "${ApiClient.profilePath}/custom-lists/${ApiClient.segment(listId)}/items", body = WatchlistStore.upsert(target))
        _itemChanges.tryEmit(ItemChange(listId))
        refresh()
    }

    /**
     * Removes a stored item (its own media type and ID, as the list returned it); a 404
     * (already gone) counts as success. A grid that already dropped the item passes
     * `reloadGrids = false` to keep its place.
     */
    suspend fun remove(item: WatchlistItem, listId: String, reloadGrids: Boolean = true) {
        try {
            ApiClient.sendRaw(
                "DELETE",
                "${ApiClient.profilePath}/custom-lists/${ApiClient.segment(listId)}/items/" +
                    "${ApiClient.segment(item.mediaType)}/${ApiClient.segment(item.id)}",
            )
        } catch (e: ApiError) {
            if (e.statusCode != 404) throw e
        }
        if (reloadGrids) _itemChanges.tryEmit(ItemChange(listId, removed = item))
        refresh()
    }

    /** Names other clients treat specially, or that are already taken. */
    fun nameProblem(name: String, renaming: String? = null): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Enter a name for the list."
            trimmed.equals("Set Aside", ignoreCase = true) -> "“Set Aside” is reserved for titles parked out of Continue Watching."
            lists.orEmpty().any { it.id != renaming && it.name.equals(trimmed, ignoreCase = true) } -> "You already have a list called “$trimmed”."
            else -> null
        }
    }
}
