package com.strmr.tv.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the Live tab lists: every channel, the favorites, or one or more categories. */
sealed interface LiveFilter {
    val label: String
    val categories: Set<String> get() = emptySet()

    data object All : LiveFilter { override val label = "All Channels" }
    data object Favorites : LiveFilter { override val label = "Favorites" }
    /** Never empty: no categories is [All] (see [selecting]). */
    data class Categories(override val categories: Set<String>) : LiveFilter {
        override val label get() = if (categories.size == 1) categories.first() else "${categories.size} Categories"
    }

    companion object {
        /** [names] as a filter: [All] when there are none. */
        fun selecting(names: Set<String>): LiveFilter = if (names.isEmpty()) All else Categories(names)
    }
}

/**
 * The Live tab's channels and guide data, for the active profile. Channels come a page at a
 * time from the server, which applies the source, category and search filters; hidden channels
 * are dropped here. Favorites are listed only under the Favorites filter, not pinned to the top
 * of the others. Favorites and hidden channels live in the
 * profile's settings (`liveTV`), shared with the Expo app; the category selection is kept on
 * this device, per profile, and restored on return. Now / next and guide schedules are fetched
 * in batches for the channels on screen. Main thread only.
 */
class LiveStore(private val scope: CoroutineScope) {
    data class Query(val filter: LiveFilter, val sourceId: String?, val search: String)

    private val profileId = ApiClient.profileId
    private var filterState = mutableStateOf(LiveFilter.selecting(LivePrefs.categories(profileId)))
    var filter: LiveFilter
        get() = filterState.value
        set(value) {
            filterState.value = value
            if (value != LiveFilter.Favorites) LivePrefs.saveCategories(profileId, value.categories)
        }
    /** Where the header's star goes back to when Favorites is turned off. */
    private var filterBeforeFavorites: LiveFilter = filter
    var sourceId by mutableStateOf<String?>(null)
    var search by mutableStateOf("")
        private set
    var showHidden by mutableStateOf(false)

    var channels by mutableStateOf<List<LiveChannel>>(emptyList())
        private set
    var total by mutableStateOf<Int?>(null)
        private set
    var categories by mutableStateOf<List<String>>(emptyList())
        private set
    var sources by mutableStateOf<List<LiveSource>>(emptyList())
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var actionError by mutableStateOf<String?>(null)
        private set
    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set
    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set
    /** null until known; the guide is offered only when the EPG is on. */
    var epgEnabled by mutableStateOf<Boolean?>(null)
        private set
    /** Now / next by guide key. */
    var nowNext by mutableStateOf<Map<String, EpgNowPlaying>>(emptyMap())
        private set
    /** Guide programmes by guide key, sorted, de-duplicated by start. */
    var schedules by mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap())
        private set

    var hasMore = true
        private set
    private var loadingFor: Query? = null
    private var loadedFor: Query? = null
    private var listsLoaded = false
    private var searchJob: Job? = null

    private val nowNextFetched = mutableMapOf<String, Long>()
    private val nowNextPending = mutableSetOf<String>()
    private var nowNextJob: Job? = null

    /** Guide data is fetched in fixed six-hour chunks from [guideBase], per key. */
    val guideBase: Long = halfHour(System.currentTimeMillis()) - HALF_HOUR
    private val chunksLoaded = mutableSetOf<String>()
    private val chunksPending = mutableMapOf<Int, MutableSet<String>>()
    private var chunkJob: Job? = null

    val query get() = Query(filter, sourceId, search)
    /** The channels to show: hidden ones only while [showHidden] is on. */
    val visibleChannels get() = if (showHidden || hidden.isEmpty()) channels else channels.filterNot { it.id in hidden }
    val isLoading get() = loadingFor != null
    /** The loaded channels are the current filters' (not a previous filter's, about to go). */
    val isCurrent get() = loadedFor == query

    // Channels

    /** First load: the profile's lists and the EPG status, then the first page. */
    suspend fun start() {
        if (!listsLoaded) {
            runCatching { ApiClient.liveChannelLists() }.getOrNull()?.let { (fav, hid) ->
                favorites = fav.toSet()
                hidden = hid.toSet()
                listsLoaded = true
            }
            epgEnabled = ApiClient.epgEnabled()
        }
        reload()
    }

    /** Debounced into [search] before it reaches the server. */
    fun setSearchText(text: String) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(400)
            if (text.trim() != search) search = text.trim()
        }
    }

    /** From the first page, unless the loaded list already matches the filters. */
    suspend fun reload(force: Boolean = false) {
        if (!force && loadedFor == query && channels.isNotEmpty()) return
        channels = emptyList()
        total = null
        hasMore = true
        loadError = null
        loadedFor = null
        loadMore()
    }

    suspend fun loadMore() {
        val requested = query
        if (!hasMore || loadingFor == requested) return
        loadingFor = requested
        val offset = if (loadedFor == requested) channels.size else 0
        val favoritesOnly = requested.filter == LiveFilter.Favorites
        try {
            val page = ApiClient.liveChannels(
                // Sent with any other filter, the server would list favorites first (and keep them
                // in every category and search), so they go only with Favorites.
                favoriteIds = if (favoritesOnly) favorites.toList() else emptyList(), favoritesOnly = favoritesOnly,
                offset = offset, limit = PAGE, sourceId = requested.sourceId,
                categories = requested.filter.categories.sorted(),
                search = requested.search,
            )
            if (requested != query) return
            val seen = if (offset == 0) mutableSetOf() else channels.map { it.id }.toMutableSet()
            val fresh = page.channels.filter { seen.add(it.id) }
            channels = if (offset == 0) fresh else channels + fresh
            hasMore = page.hasMore && page.channels.isNotEmpty()
            total = page.total
            page.availableCategories?.let { categories = it }
            page.sources?.let { srcs ->
                sources = srcs
                if (sourceId != null && srcs.none { it.id == sourceId }) sourceId = null
            }
            // Categories the server no longer lists drop out of the selection.
            val selected = requested.filter.categories
            if (selected.isNotEmpty() && categories.isNotEmpty() && !categories.containsAll(selected)) {
                filter = LiveFilter.selecting(selected.intersect(categories.toSet()))
            }
            loadedFor = requested
            loadError = null
        } catch (e: Exception) {
            if (channels.isEmpty() && requested == query) loadError = e.message
        } finally {
            if (loadingFor == requested) loadingFor = null
        }
    }

    // Favorites and hidden channels

    /** The header's star: Favorites, or back to the filter that was on before it. */
    fun toggleFavoritesOnly() {
        if (filter == LiveFilter.Favorites) {
            filter = filterBeforeFavorites
        } else {
            filterBeforeFavorites = filter
            filter = LiveFilter.Favorites
        }
    }

    /**
     * Adds or removes [name] from the selected categories (from Favorites, starts a new
     * selection); none left lists every channel.
     */
    fun toggleCategory(name: String) {
        val names = filter.categories
        filter = LiveFilter.selecting(if (name in names) names - name else names + name)
    }

    fun toggleFavorite(channel: LiveChannel) {
        val previous = favorites
        favorites = if (channel.id in favorites) favorites - channel.id else favorites + channel.id
        if (filter == LiveFilter.Favorites && channel.id !in favorites) {
            channels = channels.filterNot { it.id == channel.id }
        }
        save { favorites = previous }
    }

    fun toggleHidden(channel: LiveChannel) {
        val previous = hidden
        hidden = if (channel.id in hidden) hidden - channel.id else hidden + channel.id
        save { hidden = previous }
    }

    private fun save(revert: () -> Unit) {
        val fav = favorites.sorted()
        val hid = hidden.sorted()
        scope.launch {
            try {
                ApiClient.saveLiveChannelLists(fav, hid)
                actionError = null
            } catch (e: Exception) {
                revert()
                actionError = e.message
            }
        }
    }

    // Now / next

    /** Queues `channel` for a now / next fetch (re-fetched after two minutes). */
    fun needNowNext(channel: LiveChannel) {
        if (epgEnabled != true) return
        val key = channel.tvgId?.ifEmpty { null } ?: return
        val fetched = nowNextFetched[key]
        if (fetched != null && System.currentTimeMillis() - fetched < 120_000) return
        nowNextFetched[key] = System.currentTimeMillis()
        nowNextPending += key
        if (nowNextJob != null) return
        nowNextJob = scope.launch {
            delay(120)
            val keys = nowNextPending.toList()
            nowNextPending.clear()
            nowNextJob = null
            for (batch in keys.chunked(50)) {
                try {
                    nowNext = nowNext + ApiClient.epgNow(batch).associateBy { it.channelId }
                } catch (e: Exception) {
                    batch.forEach { nowNextFetched.remove(it) }
                }
            }
        }
    }

    /** The programme on now, from the guide data or now / next. */
    fun current(channel: LiveChannel, now: Long = System.currentTimeMillis()): EpgProgram? =
        schedules[channel.guideKey]?.firstOrNull { it.isAiring(now) } ?: nowNext[channel.guideKey]?.current?.takeIf { it.isAiring(now) }

    // Guide schedules

    /** Queues the guide chunks covering `[from, to)` for `channel`. */
    fun needSchedule(channel: LiveChannel, from: Long, to: Long) {
        if (epgEnabled != true) return
        val key = channel.guideKey
        val first = maxOf(0, ((from - guideBase) / CHUNK).toInt())
        val last = maxOf(first, ((to - guideBase - 1) / CHUNK).toInt())
        var queued = false
        for (index in first..last) {
            if (chunksLoaded.add("$index|$key")) {
                chunksPending.getOrPut(index) { mutableSetOf() } += key
                queued = true
            }
        }
        if (!queued || chunkJob != null) return
        chunkJob = scope.launch {
            delay(150)
            val pending = chunksPending.toSortedMap()
            chunksPending.clear()
            chunkJob = null
            for ((index, keys) in pending) fetchChunk(index, keys.toList())
        }
    }

    private suspend fun fetchChunk(index: Int, keys: List<String>) {
        val start = guideBase + index * CHUNK
        val offset = Math.floorDiv(start - System.currentTimeMillis(), 60_000L).toInt()
        for (batch in keys.chunked(40)) {
            try {
                // An hour over the chunk, so a clock skew can't leave a gap between chunks.
                val result = ApiClient.epgSchedule(batch, offset, (CHUNK / 3_600_000).toInt() + 1)
                val merged = schedules.toMutableMap()
                for (key in batch) merged[key] = merge(merged[key].orEmpty(), result[key].orEmpty())
                schedules = merged
            } catch (e: Exception) {
                com.strmr.tv.app.AppLog.w("Live", "guide chunk $index failed: ${e.message}")
                batch.forEach { chunksLoaded.remove("$index|$it") }
            }
        }
    }

    /** Clears the server's channel cache and reloads. */
    suspend fun refresh() {
        ApiClient.clearLiveCache()
        nowNext = emptyMap()
        nowNextFetched.clear()
        schedules = emptyMap()
        chunksLoaded.clear()
        reload(force = true)
    }

    companion object {
        private const val PAGE = 250
        const val HALF_HOUR = 1_800_000L
        const val CHUNK = 6 * 3_600_000L

        fun halfHour(ms: Long) = ms - ms % HALF_HOUR

        /** Sorted by start, one programme per start time, each cut off where the next begins. */
        private fun merge(old: List<EpgProgram>, new: List<EpgProgram>): List<EpgProgram> {
            val sorted = (old + new).filter { it.stopMs > it.startMs }.associateBy { it.startMs }.values.sortedBy { it.startMs }
            sorted.forEachIndexed { i, p ->
                p.endMs = p.stopMs
                sorted.getOrNull(i + 1)?.let { next -> if (next.startMs < p.stopMs) p.endMs = next.startMs }
            }
            return sorted
        }
    }
}
