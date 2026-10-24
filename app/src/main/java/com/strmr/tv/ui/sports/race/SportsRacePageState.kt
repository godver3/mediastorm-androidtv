package com.strmr.tv.ui.sports.race

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.sports.SportsF1Archive
import com.strmr.tv.sports.SportsMotoGPEnrichment
import com.strmr.tv.sports.SportsRaceDriver
import com.strmr.tv.sports.SportsRaceEvent
import com.strmr.tv.sports.sportsF1Archive
import com.strmr.tv.sports.sportsMotoGPCircuit
import com.strmr.tv.sports.sportsMotoGPStandings
import com.strmr.tv.sports.sportsRaceSession
import com.strmr.tv.sports.sportsRaces
import com.strmr.tv.ui.sports.SportsHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The race page's tabs; Championship is MotoGP's riders' standings. */
enum class SportsRaceTab(val label: String) { OVERVIEW("Overview"), TIMING("Timing"), CHAMPIONSHIP("Championship") }

/**
 * The race page's data (Expo `RaceScreen` and `SportsRaceDetailTV` state): the weekend from the
 * race board, the shown session and its latest snapshot, and the extras each series has (F1's
 * timing archive, MotoGP's circuit guide and championship), each loaded on its own schedule by
 * [SportsRaceScreen].
 */
@Stable
class SportsRacePageState(private val weekendId: String, initialSession: String?) {
    var weekend by mutableStateOf<SportsRaceEvent?>(null); private set
    /** Why there is no weekend to show. */
    var missing by mutableStateOf<String?>(null); private set
    var boardFailed by mutableStateOf(false); private set
    var selectedId by mutableStateOf(initialSession); private set
    private var result by mutableStateOf<SportsRaceEvent?>(null)
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var tab by mutableStateOf(SportsRaceTab.OVERVIEW)
    /** The entry the driver card and comparison show (Expo `competitorId`). */
    var competitorId by mutableStateOf<String?>(null)
    /** Timing's result filter ("Retired"…); null shows every entry. */
    var resultFilter by mutableStateOf<String?>(null)
    /** The Timing row showing its labelled statistics. */
    var expandedId by mutableStateOf<String?>(null)
    var archive by mutableStateOf<SportsF1Archive?>(null); private set
    var guide by mutableStateOf<SportsMotoGPEnrichment?>(null); private set
    var guideFailed by mutableStateOf(false); private set
    var standings by mutableStateOf<SportsMotoGPEnrichment?>(null); private set
    var standingsFailed by mutableStateOf(false); private set
    /** Bumped by Try Again; every load restarts on it. */
    var retries by mutableStateOf(0); private set

    init {
        // The hub's board shows the weekend at once; loadWeekend() brings a fresh one.
        SportsHub.state.raceBoard?.resolve(weekendId)?.let { (event, session) ->
            weekend = event
            if (selectedId == null) selectedId = session
        }
    }

    /** The session shown: the one picked, else the weekend's default (Expo `defaultRaceSession`). */
    val session: SportsRaceEvent? get() = weekend?.let { w -> w.sessions.firstOrNull { it.id == selectedId } ?: w.defaultSession }

    /** The shown session's latest snapshot: its own answer once it has come, else the board's copy. */
    val detail: SportsRaceEvent? get() = session?.let { shown -> result?.takeIf { it.id == shown.id } ?: shown }

    val competitor: SportsRaceDriver?
        get() = detail?.participants?.let { all -> all.firstOrNull { it.id == competitorId } ?: all.firstOrNull() }

    /** Updates are late: a failed load, or the provider marked the weekend or session stale. */
    val delayed: Boolean get() = boardFailed || failed || detail?.stale == true || weekend?.stale == true

    /** The results Timing can filter by, when there are two or more (Expo `resultStatuses`). */
    val resultStatuses: List<String>
        get() = detail?.participants.orEmpty().mapNotNull { it.result }.filter { RESULTS.matches(it) }.distinct().takeIf { it.size > 1 }.orEmpty()

    val activeFilter: String? get() = resultFilter?.takeIf { it in resultStatuses }

    /** The entries shown, after the result filter. */
    val entries: List<SportsRaceDriver> get() = detail?.participants.orEmpty().filter { activeFilter == null || it.result == activeFilter }

    /** The shown session's F1 timing archive, embedded or fetched. */
    val timingArchive: SportsF1Archive?
        get() = detail?.let { shown -> shown.f1Archive ?: archive?.takeIf { it.eventId == shown.id } }?.takeIf { it.available }

    val tabs: List<SportsRaceTab>
        get() = if (weekend?.riders == true) SportsRaceTab.entries else listOf(SportsRaceTab.OVERVIEW, SportsRaceTab.TIMING)

    fun select(sessionId: String) {
        selectedId = sessionId
        resultFilter = null
        expandedId = null
        if (tab == SportsRaceTab.CHAMPIONSHIP) tab = SportsRaceTab.OVERVIEW
    }

    fun retry() {
        retries++
    }

    /** The weekend from a fresh race board (Expo reloads it whenever the page shows). */
    suspend fun loadWeekend() {
        try {
            val found = ApiClient.sportsRaces().resolve(weekendId)
            if (found != null) {
                weekend = found.first
                if (selectedId == null) selectedId = found.second
                missing = null
            } else if (weekend == null) {
                missing = "This race weekend is no longer in the current feed."
            }
            boardFailed = found == null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            boardFailed = true
            if (weekend == null) missing = "Race weekends could not be reached."
        }
    }

    /** Reloads the shown session every 30 s, every 10 min once final, keeping the last answer through failures. */
    suspend fun pollSession() {
        val shown = session ?: return
        loading = true
        failed = false
        while (true) {
            try {
                result = ApiClient.sportsRaceSession(shown)
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
            loading = false
            val latest = result?.takeIf { it.id == shown.id } ?: shown
            delay(if (latest.isFinal) 600_000 else 30_000)
        }
    }

    /** F1's timing archive, for a finished F1 session that doesn't carry one. */
    suspend fun loadArchive() {
        val shown = detail ?: return
        if (shown.league != "f1" || !shown.isFinal || shown.f1Archive != null || archive?.eventId == shown.id) return
        archive = try {
            ApiClient.sportsF1Archive(shown)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** The organizer's circuit guide, for a MotoGP weekend without reference geometry. */
    suspend fun loadGuide() {
        val shown = weekend ?: return
        if (!shown.riders || shown.circuit != null) return
        guideFailed = false
        try {
            guide = ApiClient.sportsMotoGPCircuit(shown)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            guideFailed = true
        }
    }

    /** The MotoGP championship, every 5 min while its tab shows. */
    suspend fun pollStandings() {
        while (true) {
            try {
                standings = ApiClient.sportsMotoGPStandings()
                standingsFailed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                standingsFailed = true
            }
            delay(300_000)
        }
    }

    private companion object {
        val RESULTS = Regex("^(Classified|Retired|DNF|DNS|DSQ|Disqualified|Finished)$", RegexOption.IGNORE_CASE)
    }
}
