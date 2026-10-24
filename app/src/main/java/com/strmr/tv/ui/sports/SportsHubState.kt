package com.strmr.tv.ui.sports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.sports.SportsCycling
import com.strmr.tv.sports.SportsCyclingFeed
import com.strmr.tv.sports.SportsCyclingListing
import com.strmr.tv.sports.SportsFilterOption
import com.strmr.tv.sports.SportsFilterSubject
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsHubFilters
import com.strmr.tv.sports.SportsLeague
import com.strmr.tv.sports.SportsLeagueTable
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsPreferencesEnvelope
import com.strmr.tv.sports.SportsRaceBoard
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.sports.SportsRaceHub
import com.strmr.tv.sports.SportsRaceListing
import com.strmr.tv.sports.SportsStandings
import com.strmr.tv.sports.isRacing
import com.strmr.tv.sports.sportsCycling
import com.strmr.tv.sports.sportsLeagues
import com.strmr.tv.sports.sportsPreferences
import com.strmr.tv.sports.sportsRaces
import com.strmr.tv.sports.sportsScoreboard
import com.strmr.tv.sports.sportsStandings
import com.strmr.tv.sports.sportsStatus
import com.strmr.tv.ui.sports.game.SportsKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * The Sports hub's events for one day, a port of the Expo TV hub's data flow: `status`,
 * `leagues` and the day's scoreboard(s), reloaded every 30 s while anything is live, 60 s for
 * the rest of today, 5 min for other days (by the screen); the race board, polled on its own
 * cadence ([SportsRaceHub.refreshSeconds]) while racing is on; the cycling calendar every 5 min
 * while cycling is on; and league tables for the records cards lack (Expo `useSportsHubRecords`).
 * [sport] and [league] narrow the rows (Expo's Sport and League filters).
 */
class SportsHubState {
    var games by mutableStateOf<List<SportsGame>>(emptyList()); private set
    var leagues by mutableStateOf<Map<String, SportsLeague>>(emptyMap()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var updatedAt by mutableStateOf<Instant?>(null); private set
    var dated by mutableStateOf(false); private set
    /** Leagues whose scoreboard is serving older data (their cards say "Updates delayed"). */
    var staleLeagues by mutableStateOf<Set<String>>(emptySet()); private set
    private var shownDay by mutableStateOf(SportsDay.TODAY)
    var day: SportsDay
        get() = shownDay
        set(value) {
            if (value != shownDay) ordering.reset()
            shownDay = value
        }
    /** Where each of the day's cards sits in its row, kept across refreshes. */
    private val ordering = SportsHubOrdering()
    var filter by mutableStateOf(SportsFilter.ALL)
    var followingOnly by mutableStateOf(false)
    /** The Sport filter: a [SportsHubFilters.sports] id, or [SportsHubFilters.ALL]. */
    var sport by mutableStateOf(SportsHubFilters.ALL); private set
    /** The League filter: a catalog league id, a cycling race's follow id, or [SportsHubFilters.ALL]. */
    var league by mutableStateOf(SportsHubFilters.ALL)
    var preferences by mutableStateOf<SportsPreferencesEnvelope?>(null)
    /** Bumped by Try Again; the screen's load loops restart on it. */
    var retries by mutableStateOf(0); private set
    /** The race board's last answer, kept through failed reloads. */
    var raceBoard by mutableStateOf<SportsRaceBoard?>(null); private set
    var raceFailed by mutableStateOf(false); private set
    /** When the race board is next due (epoch ms): leaving the tab and coming back resumes the wait. */
    private var raceDueAt = 0L
    /** The cycling calendar's last answer, kept through failed reloads. */
    var cyclingFeed by mutableStateOf<SportsCyclingFeed?>(null); private set
    var cyclingFailed by mutableStateOf(false); private set
    private var cyclingDueAt = 0L
    /** League tables by league, for the records cards lack; only available answers are kept. */
    var tables by mutableStateOf<Map<String, List<SportsLeagueTable>>>(emptyMap()); private set
    private val tablesAsked = mutableSetOf<String>()

    /** Picks a sport; the league goes back to all of them. */
    fun chooseSport(id: String) {
        sport = id
        league = SportsHubFilters.ALL
    }

    fun clearFilters() {
        chooseSport(SportsHubFilters.ALL)
    }

    fun retry() {
        raceDueAt = 0
        cyclingDueAt = 0
        retries++
    }

    private val followed: List<String> get() = preferences?.preferences?.competitionIds.orEmpty()
    private val pinned: List<String> get() = preferences?.preferences?.pinnedSportIds.orEmpty()

    private fun followId(league: String) = leagues[league]?.followId ?: SportsLeague.followAliases[league] ?: league

    private fun family(league: String): String? =
        leagues[league]?.let(SportsHubFilters::family) ?: games.firstOrNull { it.league == league }?.sport.let(SportsHubFilters::familyOf)

    /** A followed competition or team, or the game's sport pinned (Expo's Following filter). */
    fun isFollowed(item: SportsHubItem): Boolean = when (item) {
        is SportsHubItem.Match -> {
            val game = item.game
            val teams = preferences?.preferences?.teamIds.orEmpty()
            followId(game.league) in followed || family(game.league)?.let { it in pinned } == true ||
                "${game.league}:${game.homeTeam.id}" in teams || "${game.league}:${game.awayTeam.id}" in teams
        }
        is SportsHubItem.Race -> SportsRaceHub.isFollowed(item.league, followed, pinned)
        is SportsHubItem.Cycling -> SportsCycling.isFollowed(item.listing.race, followed, pinned)
    }

    /**
     * Followed competitions first, in follow order; then pinned sports in pin order (Expo
     * `priority`); then a game with a followed team.
     */
    private fun rank(item: SportsHubItem): Int = when (item) {
        is SportsHubItem.Match -> {
            val league = item.league
            val pin = family(league)?.let(pinned::indexOf) ?: -1
            followed.indexOf(followId(league)).takeIf { it >= 0 }
                ?: if (pin >= 0) followed.size + pin else if (isFollowed(item)) followed.size + pinned.size else Int.MAX_VALUE
        }
        is SportsHubItem.Race -> SportsRaceHub.followRank(item.league, followed, pinned)
        is SportsHubItem.Cycling -> SportsCycling.followRank(item.listing.race, followed, pinned)
    }

    /** What the Sport and League filters see of a card. */
    private fun subject(item: SportsHubItem): SportsFilterSubject = when (item) {
        is SportsHubItem.Match -> SportsFilterSubject(family(item.league), item.league, leagues[item.league]?.college == true)
        is SportsHubItem.Race -> SportsFilterSubject(SportsHubFilters.MOTORSPORT, item.league)
        is SportsHubItem.Cycling -> SportsFilterSubject(SportsCycling.SPORT, item.league)
    }

    /** The Sport filter's choices: the sports with a league turned on, pinned first. */
    val sportOptions: List<SportsFilterOption> get() = SportsHubFilters.sportOptions(leagues.values, pinned)

    /** The League filter's choices for the chosen sport. */
    val leagueOptions: List<SportsFilterOption>
        get() = SportsHubFilters.leagueOptions(sport, leagues.values.toList(), cyclingFeed?.races.orEmpty(), followed, pinned)

    /** Whether the hub offers Competition Details for the chosen sport or league. */
    val hasDetails: Boolean get() = SportsHubFilters.hasDetails(sport, league, leagues)

    /** The game's sport family (which side its card lists first, its context line). */
    fun kind(game: SportsGame): SportsKind = SportsKind.of(leagues[game.league], game)

    fun leagueName(id: String) = leagues[id]?.label ?: SportsRaceFormat.seriesNames[id] ?: id.uppercase()

    val usableLeagues: List<SportsLeague> get() = leagues.values.filter { it.isUsable }.sortedBy { it.label.lowercase() }

    val hasLive: Boolean get() = games.any { it.lifecycle == SportsLifecycle.LIVE }

    /** The catalog has a racing series turned on, so the race board is worth asking for. */
    val racingEnabled: Boolean get() = leagues.values.any { it.isUsable && it.isRacing }

    /** Racing is on but its board hasn't answered yet. */
    val racesPending: Boolean get() = racingEnabled && raceBoard == null && !raceFailed

    /** The race board failed or a series is stale or unavailable (Expo's hub issue line). */
    val racingDelayed: Boolean get() = racingEnabled && (raceFailed || raceBoard?.delayed == true)

    /** The catalog has a cycling race turned on (Expo `sportEnabled('cycling')`). */
    val cyclingEnabled: Boolean get() = leagues.values.any { it.isUsable && SportsHubFilters.family(it) == SportsCycling.SPORT }

    val cyclingPending: Boolean get() = cyclingEnabled && cyclingFeed == null && !cyclingFailed

    /** Expo's "Cycling updates delayed or unavailable". */
    val cyclingDelayed: Boolean get() = cyclingEnabled && (cyclingFailed || cyclingFeed?.delayed == true)

    /** The day's cycling cards: each stage that day, or the race on a rest day. */
    private val cyclingListings: List<SportsCyclingListing>
        get() {
            if (!cyclingEnabled) return emptyList()
            val day = LocalDate.now().plusDays(this.day.offset).toString()
            return SportsCycling.listings(cyclingFeed?.races.orEmpty(), day) { true }
        }

    /** The day's race sessions, one card per weekend in each lifecycle. */
    private val raceListings: List<SportsRaceListing>
        get() {
            val board = raceBoard ?: return emptyList()
            val (start, end) = day.bounds()
            return SportsRaceHub.listings(board, start, end) { leagues[it]?.isUsable == true }
        }

    /** Live Now, Upcoming, Results and Schedule Updates; followed competitions first in each. */
    val sections: List<SportsSection>
        get() {
            val items = games.map { SportsHubItem.Match(withRecord(it)) } + raceListings.map { SportsHubItem.Race(it) } +
                cyclingListings.map { SportsHubItem.Cycling(it) }
            val sport = sport.takeIf { s -> s == SportsHubFilters.ALL || sportOptions.any { it.id == s } } ?: SportsHubFilters.ALL
            val chosen = items.filter { SportsHubFilters.matches(subject(it), sport, league) }
            val pool = if (followingOnly) chosen.filter(::isFollowed) else chosen
            // Places are handed out on first sight, filters or not, so a filter never reorders a row.
            ordering.record(items)
            val all = SportsRow.entries.map { row ->
                SportsSection(row.key, row.title, ordering.ordered(pool.filter { row.contains(it.lifecycle) }, row, ::rank))
            }
            val keys = when (filter) {
                SportsFilter.ALL -> setOf("live", "upcoming", "results", "changes")
                SportsFilter.LIVE -> setOf("live")
                SportsFilter.UPCOMING -> setOf("upcoming", "changes")
                SportsFilter.RESULTS -> setOf("results")
            }
            return all.filter { it.key in keys && it.items.isNotEmpty() }
        }

    /** Seconds until the next reload, on the Expo hub's cadence. */
    val refreshSeconds: Long get() = if (error != null || hasLive) 30 else if (day == SportsDay.TODAY) 60 else 300

    suspend fun load() {
        val api = ApiClient
        val forDay = day
        try {
            val status = api.sportsStatus()
            if (!status.enabled) throw ApiError.NotReady("Sports is turned off on this server.")
            val catalog = leagues.values.toList().ifEmpty { api.sportsLeagues() }
            val dates: List<String?> = if (status.dateQueries == true) forDay.scoreboardDates() else listOf(null)
            val boards = coroutineScope { dates.map { date -> async { api.sportsScoreboard(date) } }.map { it.await() } }
            val merged = boards.flatMap { it.games }.associateBy { it.id }.values
            if (forDay != day) return
            leagues = catalog.associateBy { it.id }
            dated = status.dateQueries == true
            staleLeagues = boards.flatMap { it.staleLeagues }.toSet()
            games = merged.filter { onDay(it, forDay) }
            updatedAt = Instant.now()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (forDay == day) error = e.message ?: "Couldn't load scores"
        } finally {
            loading = false
        }
    }

    /**
     * Reloads the race board whenever it is due, for as long as the hub shows: every 30 s while
     * a session is live, a series is delayed or the board is empty, else every 60 s; 30 s
     * after a failure, keeping the last good board (Expo `useSportsHubRaces`).
     */
    suspend fun pollRaces() {
        while (true) {
            val wait = raceDueAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            raceDueAt = try {
                val board = ApiClient.sportsRaces()
                raceBoard = board
                raceFailed = false
                System.currentTimeMillis() + SportsRaceHub.refreshSeconds(board) * 1000
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                raceFailed = true
                System.currentTimeMillis() + SportsRaceHub.RETRY_SECONDS * 1000
            }
        }
    }

    suspend fun loadPreferences() {
        preferences = runCatching { ApiClient.sportsPreferences() }.getOrNull()
    }

    /** The game with records from the league's table where the scoreboard sent none. */
    private fun withRecord(game: SportsGame): SportsGame = tables[game.league]?.let { SportsStandings.withRecords(game, it) } ?: game

    /**
     * Reloads the cycling calendar every 5 min while the hub shows (Expo's hub cycling source),
     * 30 s after a failure, keeping the last good answer.
     */
    suspend fun pollCycling() {
        while (true) {
            val wait = cyclingDueAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            cyclingDueAt = try {
                cyclingFeed = ApiClient.sportsCycling()
                cyclingFailed = false
                System.currentTimeMillis() + 300_000
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cyclingFailed = true
                System.currentTimeMillis() + 30_000
            }
        }
    }

    /**
     * Fetches the tables of the leagues whose games lack a record, two at a time, once per
     * league for the hub's life (Expo `useSportsHubRecords`); a table that can't be had is
     * skipped and the cards stay as they are.
     */
    suspend fun loadRecords() {
        val wanted = games.filter { it.awayTeam.record.isNullOrEmpty() || it.homeTeam.record.isNullOrEmpty() }
            .map { it.league }.distinct().sorted().filter { tablesAsked.add(it) }
        if (wanted.isEmpty()) return
        val queue = ArrayDeque(wanted)
        coroutineScope {
            repeat(2) {
                launch {
                    while (true) {
                        val next = queue.removeFirstOrNull() ?: break
                        try {
                            val answer = ApiClient.sportsStandings(next)
                            if (answer.available) tables = tables + (next to answer.groups)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // The scoreboard stays usable without the table.
                        }
                    }
                }
            }
        }
    }

    /** Games starting on the day, events spanning it, and (today) anything still live. */
    private fun onDay(game: SportsGame, day: SportsDay): Boolean {
        val (start, end) = day.bounds()
        val s = game.start
        if (!s.isBefore(start) && s.isBefore(end)) return true
        val e = game.end
        if (e != null && s.isBefore(end) && e.isAfter(start)) return true
        return day == SportsDay.TODAY && game.lifecycle == SportsLifecycle.LIVE
    }
}
