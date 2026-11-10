package com.strmr.tv.ui.lists

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.home.BrowseEntry
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.BrowseOptions
import com.strmr.tv.home.BrowsePage
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.home.browsePage
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.HiddenItemsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A whole list's grid state, shared by the TV and phone list pages: paged from `display-list`
 * ([PAGE] at a time) as the grid nears its end, sorted and filtered by the server
 * ([BrowseOptions]; any change reloads from the first page). [scope] is the page's own, so
 * its requests end with it. Main thread only.
 */
@Stable
class ListGridModel(
    val list: BrowseList,
    private val query: Map<String, String>,
    private val scope: CoroutineScope,
) {
    var options by mutableStateOf(BrowseOptions())
    /** A streaming service's list ([BrowseList.Service.parts]). */
    var part by mutableIntStateOf((list as? BrowseList.Service)?.part ?: 0)
    var entries by mutableStateOf<List<BrowseEntry>>(emptyList()); private set
    /** Items read so far (duplicates dropped from `entries` included). */
    var fetched by mutableIntStateOf(0); private set
    var total by mutableStateOf<Int?>(null); private set
    var genres by mutableStateOf<List<String>>(emptyList()); private set
    var letters by mutableStateOf<List<String>>(emptyList()); private set
    var loadError by mutableStateOf<String?>(null); private set
    /** A failed remove; shown over the grid. */
    var actionError by mutableStateOf<String?>(null)
    /** The [reloadKey] a page is loading for; null while idle. */
    var loadingFor by mutableStateOf<Any?>(null); private set
    var picking by mutableStateOf(false); private set
    /** Bumped when a title is added to this list elsewhere (Add to List…), so it re-reads. */
    private var added by mutableIntStateOf(0)

    val shown: BrowseList get() = if (list is BrowseList.Service) list.copy(part = part) else list
    val customId: String? get() = (list as? BrowseList.Custom)?.id
    /** The profile's list as the store has it (renamed here or elsewhere), when this is one. */
    val current get() = customId?.let { id -> CustomListsStore.lists?.firstOrNull { it.id == id } }
    val title: String get() = current?.name ?: shown.title
    /** The profile's own lists, but Set Aside, can be renamed, deleted and have titles removed. */
    val manageable: Boolean get() = current?.let { !it.isSetAside } == true
    /** Changes whenever the grid must reload from its first page. */
    val reloadKey: Any get() = listOf(options, part, added)
    val loadingMore: Boolean get() = loadingFor == reloadKey

    /** The cards to draw: ranked for Top 10, without titles hidden from the profile (but in Hidden). */
    val cards: List<HomeCard>
        get() {
            val ranked = shown is BrowseList.TopTen
            return entries.mapIndexed { i, e -> HomeCard.from(e).let { if (ranked) it.ranked(i + 1) else it } }
                .filter { list == BrowseList.Hidden || it.target?.let(HiddenItemsStore::isHidden) != true }
        }

    /** Starts over from the first page (the effect keyed on [reloadKey] calls it). */
    suspend fun reload() {
        entries = emptyList()
        fetched = 0
        total = null
        loadError = null
        loadPage()
    }

    /** Reads the next page, unless one is loading or the list has ended. */
    suspend fun loadPage() {
        val requested = reloadKey
        if (loadingFor == requested || total?.let { fetched >= it } == true) return
        loadingFor = requested
        try {
            val offset = fetched
            val quick = shown.quickPageQuery
            val page = ApiClient.browsePage(shown, limit = PAGE, offset = offset, extra = query + quick.orEmpty(), options = options)
            // The sort or a filter changed while this page was loading; that reload wins.
            if (requested != reloadKey) return
            // Then the full page's cards (same titles, with their text posters) replace the quick ones in place.
            if (quick != null) scope.launch {
                val full = runCatching { ApiClient.browsePage(shown, limit = PAGE, offset = offset, extra = query, options = options) }.getOrNull()
                if (full == null || requested != reloadKey) return@launch
                val byId = full.entries.associateBy { HomeCard.from(it).id }
                entries = entries.map { byId[HomeCard.from(it).id] ?: it }
            }
            if (fetched == 0) {
                genres = page.genres
                letters = page.letters
            }
            fetched += page.entries.size
            val seen = entries.map { HomeCard.from(it).id }.toMutableSet()
            entries = entries + page.entries.filter { seen.add(HomeCard.from(it).id) }
            total = page.listSize(fetched)
            loadError = null
        } catch (e: Exception) {
            if (entries.isEmpty() && requested == reloadKey) loadError = e.message
        } finally {
            if (loadingFor == requested) loadingFor = null
        }
    }

    /**
     * Follows this list's own changes until cancelled: an add re-reads it, a removal drops the
     * title in place (so the grid keeps its place).
     */
    suspend fun followItemChanges() {
        val id = customId ?: return
        CustomListsStore.itemChanges.collect { change ->
            if (change.listId != id) return@collect
            val removed = change.removed ?: return@collect run { added++ }
            val gone = entries.filter { (it as? BrowseEntry.ItemEntry)?.item?.let { i -> i.mediaType == removed.mediaType && i.id == removed.id } == true }
            if (gone.isEmpty()) return@collect
            entries = entries - gone.toSet()
            fetched = maxOf(0, fetched - gone.size)
            total = total?.let { maxOf(0, it - gone.size) }
        }
    }

    /**
     * Re-reads what is loaded in place (a title action changed a state or badge), paging as
     * [loadPage] does up to what was loaded, so the cards below the first page stay.
     */
    suspend fun rereadLoaded() {
        if (loadingFor != null || total == null) return
        val requested = reloadKey
        val wanted = fetched
        val read = mutableListOf<BrowseEntry>()
        var firstPage: BrowsePage? = null
        var size: Int
        do {
            val page = runCatching { ApiClient.browsePage(shown, limit = PAGE, offset = read.size, extra = query, options = options) }.getOrNull()
            if (page == null || requested != reloadKey) return
            if (firstPage == null) firstPage = page
            read += page.entries
            size = page.listSize(read.size)
        } while (page.entries.isNotEmpty() && read.size < wanted && read.size < size)
        val seen = mutableSetOf<String>()
        entries = read.filter { seen.add(HomeCard.from(it).id) }
        fetched = read.size
        total = size
        genres = firstPage?.genres.orEmpty()
        letters = firstPage?.letters.orEmpty()
    }

    /** Drops the title here at once, then tells the server; a failure puts it back. */
    fun remove(card: HomeCard) {
        val id = customId ?: return
        val entry = entries.firstOrNull { HomeCard.from(it).id == card.id } as? BrowseEntry.ItemEntry ?: return
        val previous = Triple(entries, fetched, total)
        entries = entries - entry
        fetched = maxOf(0, fetched - 1)
        total = total?.let { maxOf(0, it - 1) }
        scope.launch {
            try {
                CustomListsStore.remove(entry.item, id, reloadGrids = false)
                actionError = null
            } catch (e: Exception) {
                entries = previous.first
                fetched = previous.second
                total = previous.third
                actionError = e.message
            }
        }
    }

    /**
     * One title at a random position of the whole (filtered) list, not just the pages loaded so
     * far; [open] gets its details link, or a library show's. A library's unmatched files have
     * no page to open, so a few other picks are tried.
     */
    fun pickRandom(open: (HomeLink) -> Unit) {
        val count = total ?: fetched
        if (picking || count <= 0) return
        picking = true
        scope.launch {
            try {
                repeat(6) {
                    val offset = (0 until count).random()
                    val entry = if (offset < entries.size && fetched == entries.size) entries[offset]
                    else runCatching { ApiClient.browsePage(shown, limit = 1, offset = offset, extra = query, options = options).entries.firstOrNull() }.getOrNull()
                        ?: return@launch
                    val link = HomeCard.from(entry).link
                    if (link is HomeLink.Details || (link is HomeLink.Library && link.entry.group.isShow)) return@launch open(link)
                }
            } finally {
                picking = false
            }
        }
    }

    companion object {
        const val PAGE = 60
    }
}
