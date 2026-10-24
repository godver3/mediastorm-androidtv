package com.strmr.tv.ui.sports.cycling

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.sports.SportsCycling
import com.strmr.tv.sports.SportsCyclingRace
import com.strmr.tv.sports.SportsCyclingResult
import com.strmr.tv.sports.SportsCyclingResults
import com.strmr.tv.sports.SportsCyclingStage
import com.strmr.tv.sports.sportsCycling
import com.strmr.tv.sports.sportsCyclingStage
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsWatchTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * The cycling page's tabs (Expo's Stage|Race overview, Overall GC, Route|Source): the shown
 * stage's results, the overall standings after it, and the organizer's stage guide.
 */
enum class SportsCyclingTab { OVERVIEW, OVERALL, ROUTE }

/**
 * The cycling page's data (Expo `CyclingScreen` and `SportsCyclingDetailTV` state): the race from
 * the cycling calendar, the shown stage and its own answer (reloaded every 5 min, never for a
 * dates-only race), the tab, the result filter and the page of riders.
 */
@Stable
class SportsCyclingPageState(private val raceId: String, initialStage: String?) {
    var race by mutableStateOf<SportsCyclingRace?>(null); private set
    /** Why there is no race to show. */
    var missing by mutableStateOf<String?>(null); private set
    var feedFailed by mutableStateOf(false); private set
    var selectedId by mutableStateOf(initialStage); private set
    private var answer by mutableStateOf<SportsCyclingStage?>(null)
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var tab by mutableStateOf(SportsCyclingTab.OVERVIEW)
    /** DNF, DNS or DSQ; null shows everyone. */
    var resultFilter by mutableStateOf<String?>(null)
    /** The page of [PAGE_SIZE] entries shown. */
    var page by mutableIntStateOf(0)
    var retries by mutableIntStateOf(0); private set

    init {
        // The hub's calendar shows the race at once; loadRace() brings a fresh one.
        race = SportsHub.state.cyclingFeed?.races?.firstOrNull { it.id == raceId }
    }

    /** The stage shown: the one picked, else the latest one on or before today (Expo `defaultCyclingStage`). */
    val stage: SportsCyclingStage?
        get() = race?.let { r -> r.stages.firstOrNull { it.id == selectedId } ?: SportsCycling.defaultStage(r) }

    /** The shown stage's latest answer, else the calendar's copy. */
    val detail: SportsCyclingStage? get() = stage?.let { shown -> answer?.takeIf { it.id == shown.id } ?: shown }

    val tabs: List<SportsCyclingTab>
        get() {
            val r = race ?: return emptyList()
            return if (r.oneDay || r.championship || r.scheduleOnly) listOf(SportsCyclingTab.OVERVIEW, SportsCyclingTab.ROUTE)
            else SportsCyclingTab.entries
        }

    /** A team time trial's stage shows its team classification. */
    val teamStage: Boolean get() = race?.oneDay == false && tab == SportsCyclingTab.OVERVIEW && detail?.teamResults != null

    /** The classification the results tab lists. */
    val classification: SportsCyclingResults?
        get() {
            val shown = detail ?: return null
            return when {
                teamStage -> shown.teamResults
                race?.oneDay == true || tab != SportsCyclingTab.OVERALL -> shown.results
                else -> shown.generalClassification
            }
        }

    val rows: List<SportsCyclingResult> get() = classification?.rows.orEmpty()

    /** DNF / DNS / DSQ among the rows, in order of appearance. */
    val resultStatuses: List<String> get() = rows.mapNotNull { it.result?.ifEmpty { null } }.distinct()

    val activeFilter: String? get() = resultFilter?.takeIf { it in resultStatuses }

    val filtered: List<SportsCyclingResult> get() = rows.filter { activeFilter == null || it.result == activeFilter }

    val pages: Int get() = maxOf(1, (filtered.size + PAGE_SIZE - 1) / PAGE_SIZE)

    val currentPage: Int get() = page.coerceIn(0, pages - 1)

    val visible: List<SportsCyclingResult> get() = filtered.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE)

    /** Updates are late: a failed load, or the classification is a saved one. */
    val delayed: Boolean get() = feedFailed || failed || classification?.stale == true

    /** What Find Streams searches for: the stage with its race, while it can still be found. */
    val watchTarget: SportsWatchTarget?
        get() {
            val r = race ?: return null
            val shown = stage ?: return null
            if (!SportsCycling.canSearch(r, shown)) return null
            return SportsWatchTarget(shown.id, "${r.name} · ${shown.name}", live = false, kind = SportsCycling.SPORT, parentId = r.id)
        }

    fun select(stageId: String) {
        selectedId = stageId
        resetResults()
    }

    fun show(tab: SportsCyclingTab) {
        this.tab = tab
        resetResults()
    }

    fun filter(status: String?) {
        resultFilter = status
        page = 0
    }

    private fun resetResults() {
        resultFilter = null
        page = 0
    }

    fun retry() {
        retries++
    }

    /** The race from a fresh calendar (Expo reloads it whenever the page shows). */
    suspend fun loadRace() {
        try {
            val found = ApiClient.sportsCycling().races.firstOrNull { it.id == raceId }
            if (found != null) {
                race = found
                missing = null
            } else if (race == null) {
                missing = "This cycling race is not available in the current feed."
            }
            feedFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            feedFailed = true
            if (race == null) missing = "Cycling coverage could not be reached."
        }
    }

    /** Reloads the shown stage every 5 min, keeping the last answer through failures; a dates-only race has none. */
    suspend fun pollStage() {
        val shown = stage ?: return
        if (race?.scheduleOnly == true) return
        loading = true
        failed = false
        while (true) {
            try {
                answer = ApiClient.sportsCyclingStage(shown)
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
            loading = false
            delay(300_000)
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
