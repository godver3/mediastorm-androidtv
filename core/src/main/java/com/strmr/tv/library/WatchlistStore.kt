package com.strmr.tv.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.WatchlistItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The profile's watchlist, shared by the Watchlist tab, long-press menus and the details
 * toggle so a change in one shows everywhere. Writes update the list optimistically, then
 * re-read the server's copy (canonical IDs, artwork, release badges). Main thread only.
 */
object WatchlistStore {
    var items by mutableStateOf<List<WatchlistItem>>(emptyList())
        private set
    var isLoaded by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    /**
     * Titles toggled this session, by title ID. A details page's `inWatchlist` comes from its
     * bundle and goes stale after a toggle; this wins over it.
     */
    private val overrides = mutableStateMapOf<String, Boolean>()
    private var refreshing: CompletableDeferred<Unit>? = null

    /** Loads the list; concurrent callers share one request. */
    suspend fun refresh() {
        refreshing?.let { return it.await() }
        val done = CompletableDeferred<Unit>()
        refreshing = done
        try {
            items = ApiClient.sendRaw("GET", "${ApiClient.profilePath}/display-list", mapOf("source" to "watchlist"))
                .let { ApiClient.json.decodeFromString<com.strmr.tv.api.DisplayListPage<WatchlistItem>>(it).items }
            loadError = null
        } catch (e: Exception) {
            if (!isLoaded || items.isEmpty()) loadError = e.message
        } finally {
            isLoaded = true
            refreshing = null
            done.complete(Unit)
        }
    }

    /** Forgets everything (profile switch). */
    fun reset() {
        items = emptyList()
        isLoaded = false
        overrides.clear()
    }

    /** Membership: a toggle made this session, else the server's `inWatchlist`, else a match against the loaded list. */
    fun contains(target: DetailsTarget, server: Boolean? = null): Boolean =
        overrides[target.titleId] ?: server ?: items.any { matches(it, target) }

    suspend fun toggle(target: DetailsTarget, server: Boolean? = null) = if (contains(target, server)) remove(target) else add(target)

    suspend fun add(target: DetailsTarget) {
        overrides[target.titleId] = true
        try {
            ApiClient.sendRaw("POST", "${ApiClient.profilePath}/watchlist", body = upsert(target))
        } catch (e: Exception) {
            overrides.remove(target.titleId)
            throw e
        }
        refresh()
        overrides[target.titleId] = true // keep the button steady if the re-read failed
    }

    suspend fun remove(target: DetailsTarget) {
        overrides[target.titleId] = false
        val previous = items
        items = items.filterNot { matches(it, target) }
        try {
            ApiClient.sendRaw(
                "DELETE",
                "${ApiClient.profilePath}/watchlist/${if (target.isMovie) "movie" else "series"}/${ApiClient.segment(target.titleId)}",
            )
        } catch (e: ApiError) {
            // 404: already gone, which counts as removed.
            if (e.statusCode != 404) {
                overrides.remove(target.titleId)
                items = previous
                throw e
            }
        }
        refresh()
        overrides[target.titleId] = false
    }

    /** The body of `POST /watchlist` and of adding to a custom list; the server rejects unknown fields. */
    fun upsert(t: DetailsTarget) = buildJsonObject {
        put("id", t.titleId)
        put("mediaType", if (t.isMovie) "movie" else "series")
        put("name", t.name)
        t.overview?.let { put("overview", it) }
        t.year?.let { put("year", it) }
        t.posterUrl?.let { put("posterUrl", it) }
        t.textPosterUrl?.let { put("textPosterUrl", it) }
        t.backdropUrl?.let { put("backdropUrl", it) }
        val ids = listOfNotNull(t.tmdbId?.let { "tmdb" to it }, t.imdbId?.let { "imdb" to it }, t.tvdbId?.let { "tvdb" to it })
        if (ids.isNotEmpty()) putJsonObject("externalIds") { ids.forEach { (k, v) -> put(k, v) } }
    }

    /** Fallback match for servers without `inWatchlist`: the same ID, or a shared provider ID of the same kind. */
    fun matches(item: WatchlistItem, target: DetailsTarget): Boolean {
        if (item.isMovie != target.isMovie) return false
        if (item.id == target.titleId) return true
        val ids = item.externalIds ?: return false
        return listOf("tmdb" to target.tmdbId, "imdb" to target.imdbId, "tvdb" to target.tvdbId)
            .any { (key, value) -> !value.isNullOrEmpty() && ids[key] == value }
    }

}
