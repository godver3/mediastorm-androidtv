package com.strmr.tv.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.home.BrowseEntry
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.BrowseOptions
import com.strmr.tv.home.BrowseSort
import com.strmr.tv.home.CardBadge
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.home.browsePage
import com.strmr.tv.home.filters
import com.strmr.tv.home.indexesLetters
import com.strmr.tv.home.offersRandom
import com.strmr.tv.home.sorts
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.TitleActions
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.pageBackground
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.PosterGrid
import com.strmr.tv.ui.fullWidth
import kotlinx.coroutines.launch

private const val PAGE = 60

/**
 * A whole list as a poster grid, paged from `display-list` as focus nears the end: what a
 * Lists tile, an explore card, a streaming service, a collection hub card or Ask AI opens.
 * The server sorts and filters ([BrowseOptions]); any change reloads from the first page.
 * The profile's own lists can be renamed or deleted here, and long press removes a title.
 */
@Composable
fun ListGridScreen(list: BrowseList, query: Map<String, String> = emptyMap()) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf(BrowseOptions()) }
    var part by remember { mutableIntStateOf((list as? BrowseList.Service)?.part ?: 0) }
    var entries by remember { mutableStateOf<List<BrowseEntry>>(emptyList()) }
    /** Items read so far (duplicates dropped from `entries` included). */
    var fetched by remember { mutableIntStateOf(0) }
    var total by remember { mutableStateOf<Int?>(null) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    var letters by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var loadingFor by remember { mutableStateOf<Any?>(null) }
    var picking by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    /** Bumped when a title is added to this list elsewhere (Add to List…), so it re-reads. */
    var added by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    val status = remember { FocusRequester() }

    val shown = if (list is BrowseList.Service) list.copy(part = part) else list
    val customId = (list as? BrowseList.Custom)?.id
    val current = customId?.let { id -> CustomListsStore.lists?.firstOrNull { it.id == id } }
    // A custom list shows its current name (it may have been renamed here or elsewhere).
    val title = current?.name ?: shown.title
    val manageable = current != null && !current.isSetAside
    // A title action (Set Aside, hide / unhide, watched) re-reads the list, so its state and badges show.
    val reloadKey = listOf(options, part, added, TitleActions.changes)

    suspend fun loadPage() {
        val requested = reloadKey
        if (loadingFor == requested || total?.let { fetched >= it } == true) return
        loadingFor = requested
        try {
            val page = ApiClient.browsePage(shown, limit = PAGE, offset = fetched, extra = query, options = options)
            // The sort or a filter changed while this page was loading; that reload wins.
            if (requested != reloadKey) return
            if (fetched == 0) {
                genres = page.genres
                letters = page.letters
            }
            fetched += page.entries.size
            val seen = entries.map { HomeCard.from(it).id }.toMutableSet()
            entries = entries + page.entries.filter { seen.add(HomeCard.from(it).id) }
            // A short page means the list ended, whatever the total said.
            total = if (page.entries.size < PAGE) fetched else page.total
            loadError = null
        } catch (e: Exception) {
            if (entries.isEmpty() && requested == reloadKey) loadError = e.message
        } finally {
            if (loadingFor == requested) loadingFor = null
        }
    }

    // Only this list's own changes: an add re-reads it, a removal drops the title in place so focus stays near it.
    LaunchedEffect(customId) {
        if (customId == null) return@LaunchedEffect
        CustomListsStore.itemChanges.collect { change ->
            if (change.listId != customId) return@collect
            val removed = change.removed ?: return@collect run { added++ }
            val gone = entries.filter { (it as? BrowseEntry.ItemEntry)?.item?.let { i -> i.mediaType == removed.mediaType && i.id == removed.id } == true }
            if (gone.isEmpty()) return@collect
            entries = entries - gone.toSet()
            fetched = maxOf(0, fetched - gone.size)
            total = total?.let { maxOf(0, it - gone.size) }
        }
    }

    LaunchedEffect(reloadKey) {
        entries = emptyList()
        fetched = 0
        total = null
        loadError = null
        loadPage()
    }
    // Once the first cards are laid out (a frame after they arrive), focus the first.
    LaunchedEffect(entries.isNotEmpty()) {
        if (entries.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    val ranked = shown is BrowseList.TopTen
    val cards = entries.mapIndexed { i, e -> HomeCard.from(e).let { if (ranked) it.copy(badge = CardBadge("#${i + 1}")) else it } }
        // Titles hidden from this profile are left out, except in the Hidden list itself.
        .filter { list == BrowseList.Hidden || it.target?.let(HiddenItemsStore::isHidden) != true }
    // Nothing left to show (the last title removed, unhidden or hidden here): the status line
    // takes focus, so the page keeps it and Back still closes just this page.
    val settled = cards.isEmpty() && (loadError != null || total != null)
    LaunchedEffect(settled) {
        if (!settled) return@LaunchedEffect
        withFrameNanos { }
        runCatching { status.requestFocus() }
    }

    /** Drops the title here at once so focus keeps its place, then tells the server. */
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

    /** One title at a random position of the whole (filtered) list, not just the pages loaded so far. */
    fun pickRandom() {
        val count = total ?: fetched
        if (picking || count <= 0) return
        picking = true
        scope.launch {
            try {
                // A library's unmatched files have no page to open: try a few other picks.
                repeat(6) {
                    val offset = (0 until count).random()
                    val entry = if (offset < entries.size && fetched == entries.size) entries[offset]
                    else runCatching { ApiClient.browsePage(shown, limit = 1, offset = offset, extra = query, options = options).entries.firstOrNull() }.getOrNull()
                        ?: return@launch
                    val link = HomeCard.from(entry).link
                    when {
                        link is HomeLink.Details -> return@launch navigator.openDetails(link.target)
                        link is HomeLink.Library && link.entry.group.isShow -> return@launch navigator.open(Page.LibraryShow(link.entry))
                    }
                }
            } finally {
                picking = false
            }
        }
    }

    Box(Modifier.fillMaxSize().pageBackground()) {
        PosterGrid(
            cards,
            first = first,
            contentPadding = PaddingValues(horizontal = PageMargin, vertical = 32.dp),
            onNearEnd = { scope.launch { loadPage() } },
            continueWatching = list == BrowseList.ContinueWatching,
            // Set Aside titles leave through the menu's Remove from Set Aside.
            extraActions = { card -> if (manageable) listOf(DialogChoice("Remove from List") { remove(card) }) else emptyList() },
            header = {
                fullWidth("header") {
                    Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            total?.let { Text(if (it == 1) "1 title" else "$it titles", color = Color.White.copy(alpha = 0.6f)) }
                        }
                        val parts = (list as? BrowseList.Service)?.service?.lists?.mapIndexed { i, l -> l.title ?: "List ${i + 1}" }.orEmpty()
                        if (list.sorts.isNotEmpty() || list.filters || list.offersRandom || parts.size > 1) {
                            BrowseControls(
                                list, options, { options = it }, genres, parts, part, { part = it }, picking,
                                onRandom = if (list.offersRandom) ::pickRandom else null,
                                onRename = if (manageable) { { renaming = true } } else null,
                                onDelete = if (manageable) { { deleting = true } } else null,
                            )
                        }
                        actionError?.let { Text(it, color = Color(0xFFFF6B6B)) }
                        if (list.indexesLetters && options.sort == BrowseSort.NAME && letters.isNotEmpty()) {
                            LetterIndex(letters, options.letter) { options = options.copy(letter = it) }
                        }
                    }
                }
            },
            footer = {
                if (cards.isEmpty()) fullWidth("status") {
                    Box(Modifier.focusRequester(status).focusable().padding(24.dp)) {
                        when {
                            loadError != null -> Text(loadError!!, color = Color(0xFFFF6B6B))
                            total != null -> Text(
                                if (options.isFiltered || options.letter != null) "No titles match these filters." else "Nothing in this list yet.",
                                color = Color.White.copy(alpha = 0.6f),
                            )
                            else -> CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    }
                }
            },
        )
    }

    if (renaming && customId != null) {
        ListNameDialog("Rename List", confirm = "Rename", initial = title, renaming = customId, onDismiss = { renaming = false }) { name ->
            if (name != title) CustomListsStore.rename(customId, name)
        }
    }
    if (deleting && customId != null) {
        ChoiceDialog(
            title = "Delete “$title”?",
            message = "The titles stay in your library; only the list goes.",
            choices = listOf(
                DialogChoice("Delete List") {
                    scope.launch {
                        try {
                            CustomListsStore.delete(customId)
                            navigator.close()
                        } catch (e: Exception) {
                            actionError = e.message
                        }
                    }
                },
                DialogChoice("Cancel") {},
            ),
            onDismiss = { deleting = false },
        )
    }
}
