package com.strmr.tv.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.AppSession
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.SetAsideStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A place on Home: a row of cards, or the place a row holds while it loads or after it failed. */
sealed interface HomeSlot {
    val id: String
    val title: String

    data class Shelf(val shelf: HomeShelf) : HomeSlot {
        override val id get() = shelf.id
        override val title get() = shelf.title
    }

    data class Loading(override val id: String, override val title: String) : HomeSlot
    data class Failed(override val id: String, override val title: String) : HomeSlot
}

/**
 * Home's rows, built from the profile's `homeShelves` config: every enabled shelf the app
 * knows, in the configured order, each loaded from its own source. Rows that come back empty
 * are left out, as on the Expo TV home; a row still loading, or that failed with nothing to
 * show, keeps its place as a placeholder. One store per Home view ([view]). Main thread only.
 */
class HomeStore(private val scope: CoroutineScope, val view: String = HomeViews.ALL) {
    var slots by mutableStateOf<List<HomeSlot>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var settings by mutableStateOf<HomeSettings?>(null)
        private set

    private var plan: List<PlannedShelf> = emptyList()
    private val rows = mutableMapOf<String, HomeShelf>()
    private val loading = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()
    private val completions = mutableMapOf<String, Job>()
    /** The manifest revision of the last full load; a different one means reload. */
    private var revision: String? = null
    private var generation = 0
    /** Full loads and staleness checks running: overlapping triggers (re-entering Home after playing) skip. */
    private var loadsRunning = 0
    private var checksRunning = 0
    /** Rows show as they arrive once the page is up; before that it waits on the top row. */
    private var revealed = false
    /** Calendar rows waiting on a server calendar still being built: they keep loading and retry. */
    private val calendarPending = mutableSetOf<String>()
    private var calendarRetries = 0
    private var calendarRetry: Job? = null

    init {
        // Hiding or showing a row in Settings › Home Layout republishes.
        scope.launch { snapshotFlow { HomeLayoutStore.hiddenShelves }.collect { publish() } }
        // Hiding or unhiding a title drops it from, or returns it to, every row.
        scope.launch { snapshotFlow { HiddenItemsStore.revision to HiddenItemsStore.items }.collect { publish() } }
    }

    /** Reloads the config and every row. Rows already shown stay until their new cards arrive. */
    suspend fun load() {
        loadsRunning++
        try {
            loadNow()
        } finally {
            loadsRunning--
        }
    }

    /** Loads unless a load is already running (opening Home and the playback rows' reload both ask). */
    suspend fun ensureLoaded() {
        if (loadsRunning == 0) load()
    }

    private suspend fun loadNow() {
        val gen = ++generation
        calendarRetry?.cancel()
        calendarRetries = 0
        // Titles hidden from this profile are left out of every row (as the Expo TV home does).
        scope.launch { HiddenItemsStore.ensureLoaded() }
        scope.launch { SetAsideStore.refresh() }
        val rev = scope.async { ApiClient.homeRevision() }
        val loaded = try {
            ApiClient.homeSettings(view)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (slots.isEmpty()) loadError = e.message
            isLoading = false
            return
        }
        revision = rev.await()
        if (gen != generation) return
        HomeViewStore.update(loaded.views)
        // The view is gone: Home falls back to All, another store.
        if (loaded.view != view) return
        settings = loaded
        plan = PlannedShelf.plan(loaded, AppSession.profile.value)
        publishLayout()
        val skipped = loaded.shelves.filter { c -> c.enabled == true && plan.none { it.id == c.id } }.map { it.id }
        AppLog.i("Home", "view $view: shelves: ${plan.joinToString { it.id }}" + if (skipped.isEmpty()) "" else "; not supported: ${skipped.joinToString()}")
        rows.keys.retainAll(plan.map { it.id }.toSet())
        loadRows(plan, gen)
    }

    /** Tells Settings › Home Layout this view's profile layout and rows (on load, and on returning to the view). */
    fun publishLayout() {
        val settings = settings ?: return
        HomeLayoutStore.update(settings.layout, plan.map { ShelfLayoutInfo(it.id, it.config.title, ShelfStyle.isLandscapeByDefault(it.config)) })
    }

    /** Reloads only when the server's home manifest changed (returning to Home, waking, every few minutes). */
    suspend fun refreshIfStale() {
        val known = revision ?: return
        if (isLoading || loadsRunning > 0 || checksRunning > 0) return
        checksRunning++
        try {
            val current = ApiClient.homeRevision()
            if (current != null && current != known) return load()
            val retry = plan.filter { it.id in failed || (it.id in calendarPending && calendarRetry?.isActive != true) }
            if (retry.isNotEmpty()) loadRows(retry, generation)
        } finally {
            checksRunning--
        }
    }

    /** Reloads the rows built from Continue Watching and playback (after playing). */
    suspend fun reloadPlaybackRows() {
        if (settings == null) return ensureLoaded()
        // A full load running fetches these rows too.
        if (loadsRunning > 0) return
        loadRows(plan.filter { it.source.followsPlayback }, generation)
    }

    /** Reloads the Watchlist row (a title added to or removed from the watchlist). */
    suspend fun reloadWatchlistRow() {
        if (settings == null) return
        loadRows(plan.filter { it.source == HomeSource.Watchlist }, generation)
    }

    private suspend fun loadRows(planned: List<PlannedShelf>, gen: Int) {
        val settings = settings ?: return
        if (planned.isEmpty()) {
            publish()
            isLoading = false
            return
        }
        val loadScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
        val context = HomeContext(settings, planned, loadScope)
        revealed = slots.isNotEmpty()
        // A row already showing cards reloads straight to its complete form.
        val phases = planned.associate { it.id to if (rows[it.id] == null) ShelfPhase.CARDS else ShelfPhase.COMPLETE }
        planned.forEach {
            completions.remove(it.id)?.cancel()
            failed -= it.id
            if (rows[it.id] == null) loading += it.id
        }
        if (revealed) publish()
        val reveal = scope.launch {
            // A first load waits this long, and for the top row, before showing anything, so
            // rows that answer at once never flash a placeholder.
            delay(1_000)
            if (gen == generation && !revealed && topRowReady) reveal()
        }
        var failure: Exception? = null
        val toComplete = mutableListOf<PlannedShelf>()
        val pendingNow = mutableListOf<PlannedShelf>()
        planned.map { shelf ->
            loadScope.async {
                val started = System.currentTimeMillis()
                val result = runCatching { shelf.load(context, phases[shelf.id] ?: ShelfPhase.CARDS) }
                if (gen != generation) return@async
                loading -= shelf.id
                result.onSuccess { row ->
                    calendarPending -= shelf.id
                    if (row == null) rows -= shelf.id else rows[shelf.id] = row
                    AppLog.i("Home", "${shelf.id}: ${row?.cards?.size ?: 0} cards${row?.kinds ?: ""} in ${System.currentTimeMillis() - started} ms")
                    if (row != null && row.cards.isNotEmpty() && phases[shelf.id] == ShelfPhase.CARDS && shelf.completesLater) {
                        toComplete += shelf
                    }
                }.onFailure { e ->
                    if (e is CancellationException) return@async
                    if (e is HomeCalendarPending) {
                        // Not a failure: the row keeps its loading placeholder until a retry fills it.
                        if (rows[shelf.id] == null) loading += shelf.id
                        calendarPending += shelf.id
                        pendingNow += shelf
                        if (revealed) publish()
                        return@async
                    }
                    AppLog.w("Home", "${shelf.id}: failed: ${e.message}")
                    if (rows[shelf.id] == null) failed += shelf.id
                    if (shelf.id == HomeShelf.CONTINUE_WATCHING_ID || failure == null) failure = e as? Exception
                }
                if (revealed) publish() else if (reveal.isCompleted && topRowReady) reveal()
            }
        }.awaitAll()
        reveal.cancel()
        if (gen != generation) return
        revealed = true
        if (pendingNow.isEmpty()) {
            if (planned.any { it.source == HomeSource.Calendar || it.source is HomeSource.RecentlyAired }) calendarRetries = 0
        } else {
            retryPendingCalendar(pendingNow, gen)
        }
        publish()
        // Complete passes wait for every row's first cards, so none queues ahead of an empty row.
        toComplete.forEach { complete(it, context, gen) }
        // The home screen's rows follow the All view's load (and its Continue Watching fetch).
        if (view == HomeViews.ALL) com.strmr.tv.launcher.LauncherSync.homeLoaded(context.loadedContinueWatching(), settings.hideWatched)
        // Nothing to show at all: the page shows the error instead of a column of failed rows.
        loadError = if (slots.none { it is HomeSlot.Shelf }) failure?.message else null
        isLoading = false
    }

    /**
     * A progressive row's complete form (full metadata, exact total, and for a [PlannedShelf.startsPartial]
     * row the rest of its cards) behind its first cards. A failure keeps the first cards.
     */
    private fun complete(shelf: PlannedShelf, context: HomeContext, gen: Int) {
        completions[shelf.id] = scope.launch {
            val started = System.currentTimeMillis()
            // A few at a time: each can take a while on a cold server and holds a connection.
            val row = completing.withPermit {
                runCatching { shelf.load(context, ShelfPhase.COMPLETE) }
                    .onFailure { if (it !is CancellationException) AppLog.w("Home", "${shelf.id}: completing failed: ${it.message}") }
                    .getOrNull()
            } ?: return@launch
            if (gen != generation || row.cards.isEmpty()) return@launch
            AppLog.i("Home", "${shelf.id}: completed to ${row.cards.size} cards in ${System.currentTimeMillis() - started} ms")
            rows[shelf.id] = row
            completions -= shelf.id
            publish()
        }
    }

    /**
     * The server is still building the calendar (right after a restart): ask again in a few
     * seconds, a few times, then quietly drop the rows until the next refresh tries again.
     */
    private fun retryPendingCalendar(shelves: List<PlannedShelf>, gen: Int) {
        // No cancel of the previous retry here: this usually runs inside it.
        if (calendarRetries >= CALENDAR_RETRIES) {
            AppLog.i("Home", "calendar still building after $calendarRetries retries; giving up for now")
            calendarRetries = 0
            loading -= shelves.map { it.id }.toSet()
            return
        }
        calendarRetries++
        AppLog.i("Home", "calendar still building; retry $calendarRetries/$CALENDAR_RETRIES in ${CALENDAR_RETRY_MS / 1000} s")
        calendarRetry = scope.launch {
            delay(CALENDAR_RETRY_MS)
            if (gen == generation) loadRows(shelves, gen)
        }
    }

    private fun reveal() {
        revealed = true
        publish()
        isLoading = false
    }

    /** The first row with cards is in, and no row above it is still loading. */
    private val topRowReady: Boolean
        get() {
            for (shelf in visiblePlan) {
                if (rows[shelf.id]?.cards?.isNotEmpty() == true) return true
                if (shelf.id in loading) return false
            }
            return false
        }

    private val visiblePlan get() = plan.filterNot { HomeLayoutStore.isHidden(it.id) }

    /** The row without titles hidden from this profile. */
    private fun visible(row: HomeShelf): HomeShelf {
        if (HiddenItemsStore.items.isEmpty()) return row
        val hidden = row.cards.filter { card -> card.target?.let(HiddenItemsStore::isHidden) == true }
        return hidden.fold(row) { r, card -> r.removing(card.id) }
    }

    private fun publish() {
        val next = visiblePlan.mapNotNull { shelf ->
            val row = rows[shelf.id]
            when {
                row != null -> visible(row).let { if (it.cards.isEmpty()) null else HomeSlot.Shelf(it) }
                shelf.id in loading -> HomeSlot.Loading(shelf.id, shelf.config.title)
                shelf.id in failed -> HomeSlot.Failed(shelf.id, shelf.config.title)
                else -> null
            }
        }
        if (next != slots) slots = next
    }

    private companion object {
        /** Complete passes in flight across every Home view. */
        val completing = Semaphore(4)
        const val CALENDAR_RETRIES = 6
        const val CALENDAR_RETRY_MS = 10_000L
    }
}

/** " (3 movies, 2 series)" for the log: what a Home view's row really holds. */
private val HomeShelf.kinds: String
    get() {
        val targets = cards.mapNotNull { it.target }
        if (targets.isEmpty()) return ""
        return " (${targets.count { it.isMovie }} movies, ${targets.count { !it.isMovie }} series)"
    }
