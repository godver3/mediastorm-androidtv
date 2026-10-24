package com.strmr.tv.ui.sports.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.sports.SportsMotoGPEnrichment
import com.strmr.tv.sports.SportsRaceBoard
import com.strmr.tv.sports.SportsRaceEvent
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.sports.SportsRaceHub
import com.strmr.tv.sports.isRacing
import com.strmr.tv.sports.sportsMotoGPStandings
import com.strmr.tv.sports.sportsRaces
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.PickerDialog
import com.strmr.tv.ui.sports.SportsBrowseHeading
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsLive
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsNotice
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsChipRow
import com.strmr.tv.ui.sports.standings.SportsLeagueStandings
import com.strmr.tv.ui.sports.standings.SportsStandingsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Motorsport's Competition Details (Expo `SportsRacingHub`): Race Weekends (every weekend of the
 * board, followed series first, live then upcoming then results, filtered by series, state and
 * favourites) or Championship Standings (the chosen series' next weekend, then its table:
 * MotoGP's riders, the others' `/standings`). The board reloads every 30 s (60 s on standings).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsRacingCalendar(initialSeries: String?) {
    val hub = SportsHub.state
    var board by remember { mutableStateOf(hub.raceBoard) }
    var failed by remember { mutableStateOf(false) }
    var retries by remember { mutableStateOf(0) }
    var championship by rememberSaveable { mutableStateOf(false) }
    var series by rememberSaveable { mutableStateOf(initialSeries) }
    var status by rememberSaveable { mutableStateOf("all") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(retries, championship) {
        while (true) {
            try {
                board = ApiClient.sportsRaces()
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
            delay(if (championship) 60_000 else 30_000)
        }
    }
    val followed = hub.preferences?.preferences?.competitionIds.orEmpty()
    val pinned = hub.preferences?.preferences?.pinnedSportIds.orEmpty()
    val seriesIds = hub.leagues.values.filter { it.isUsable && it.isRacing }.map { it.id }
    val events = board?.events.orEmpty().filter { e ->
        e.league in seriesIds && (series == null || e.league == series) && (status == "all" || e.status == status) &&
            (!favorites || SportsRaceHub.isFollowed(e.league, followed, pinned))
    }.sortedWith(weekendOrder(followed))
    val tabFocus = remember { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SportsBrowseHeading(
            "MOTORSPORT / RACE CONTROL", if (championship) "Championship Standings" else "Race Weekends",
            if (championship) "Published season tables. Select a series to explore its championship."
            else "Session schedules, classifications and circuit guides. Times shown locally.",
        )
        SportsChipRow(tabFocus) {
            listOf(false to "Race Weekends", true to "Championship Standings").forEach { (value, label) ->
                FilterChip(selected = championship == value, onClick = { championship = value },
                           modifier = if (championship == value) Modifier.focusRequester(tabFocus) else Modifier) { Text(label) }
            }
            OutlinedButton(onClick = { picking = true }) {
                Text("Series: ${series?.let(hub::leagueName) ?: "All Racing"}")
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            if (!championship) FilterChip(selected = favorites, onClick = { favorites = !favorites }) { Text("My Favorites") }
        }
        if (championship) {
            Championship(series, board, failed)
        } else {
            val statusFocus = remember { FocusRequester() }
            SportsChipRow(statusFocus) {
                listOf("all" to "All Weekends", "scheduled" to "Upcoming Races", "final" to "Race Results").forEach { (id, label) ->
                    FilterChip(selected = status == id, onClick = { status = id },
                               modifier = if (status == id) Modifier.focusRequester(statusFocus) else Modifier) { Text(label) }
                }
            }
            Text("${events.size} weekends in this selection · ESPN / MotoGP", fontSize = 13.sp, color = SportsMuted)
            if (board == null && !failed) SportsNotice("Loading race weekends", "Fetching the race calendar.", loading = true)
            if (failed || board?.delayed == true) {
                val late = board?.leagues.orEmpty().filter { it.stale || it.unavailable }
                    .joinToString(" · ") { "${hub.leagueName(it.league)}: ${if (it.unavailable) "unavailable" else "updates delayed"}" }
                SportsNotice("Racing updates delayed",
                             if (failed) "Racing updates are unavailable. Any displayed events are from the last successful update." else late,
                             onRetry = { retries++ })
            }
            if (board != null && events.isEmpty() && !failed) {
                Text(if (favorites) "No favorite race weekends match. Follow a series in Customize Sports, or show all racing."
                     else "No race weekends returned for this selection.", fontSize = 13.sp, color = SportsMuted)
            }
            events.forEach { WeekendRow(it) }
        }
    }
    if (picking) {
        val ids = listOf<String?>(null) + seriesIds
        PickerDialog(
            title = "Series", options = ids.map { it?.let(hub::leagueName) ?: "All Racing" }, selected = ids.indexOf(series),
            onPick = { series = ids[it] }, onDismiss = { picking = false },
        )
    }
}

/** Followed series first, then live, upcoming (soonest first), results (latest first). */
private fun weekendOrder(followed: List<String>) = Comparator<SportsRaceEvent> { a, b ->
    fun follows(e: SportsRaceEvent) = if (SportsRaceHub.followId(e.league) in followed) 0 else 1
    fun state(e: SportsRaceEvent) = when (e.status) { "live" -> 0; "scheduled" -> 1; else -> 2 }
    val start = { e: SportsRaceEvent -> e.start ?: Instant.EPOCH }
    compareValuesBy(a, b, ::follows, ::state).takeIf { it != 0 }
        ?: if (a.status == "final") start(b).compareTo(start(a)) else start(a).compareTo(start(b))
}

/** A weekend (Expo's weekend card): series and state, title, venue, the shown session and its winner, every session. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekendRow(weekend: SportsRaceEvent) {
    val navigator = LocalNavigator.current
    val session = weekend.defaultSession
    val winner = session?.takeIf { it.isFinal }?.participants?.firstOrNull { it.winner }
    // On a panel, so each one reads as a card between its neighbours.
    SportsPanel(title = null) {
    SportsReadout(onClick = { navigator.open(Page.SportsRace(weekend.id)) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(SportsHub.state.leagueName(weekend.league), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Accent)
            Text((session?.status ?: weekend.status).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                 color = if (session?.isLive == true) SportsLive else SportsMuted)
        }
        Text(SportsRaceFormat.cardTitle(weekend.title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        weekend.venue?.ifEmpty { null }?.let { Text(it, fontSize = 13.sp, color = SportsMuted) }
        Text("${session?.sessionType ?: "Session"} · ${SportsRaceFormat.date(session?.start ?: weekend.start)}", fontSize = 13.sp, color = SportsMuted)
        winner?.let {
            Text("${it.name} · ${if (session.sessionType.orEmpty().startsWith("Race")) "Winner" else "Session winner"}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            weekend.subEvents.forEach { s ->
                Column {
                    Text(s.sessionType ?: "Session", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (s.isLive) SportsLive else Accent)
                    Text(SportsRaceFormat.date(s.start), fontSize = 12.sp, color = SportsMuted)
                    Text(if (s.isFinal) "Completed" else if (s.isLive) "Live" else "Scheduled", fontSize = 12.sp, color = SportsMuted)
                }
            }
        }
        val entries = session?.participants?.size ?: 0
        Text("${weekend.subEvents.size} ${if (weekend.subEvents.size == 1) "session" else "sessions"} · ${if (entries > 0) "$entries ${if (weekend.riders) "riders" else "drivers"}" else "View session details"}",
             fontSize = 12.sp, color = SportsMuted)
    }
    }
}

/**
 * The chosen series' championship: its next (or live) weekend with Open Race Weekend, then the
 * table; All Racing asks for a series first.
 */
@Composable
private fun Championship(series: String?, board: SportsRaceBoard?, failed: Boolean) {
    if (series == null) {
        Text("Choose a series above to view its championship standings.", fontSize = 13.sp, color = SportsMuted)
        return
    }
    val navigator = LocalNavigator.current
    val now = Instant.now()
    val next = board?.events.orEmpty().filter { it.league == series }.mapNotNull { e ->
        (listOf(e) + e.subEvents).filter { it.isLive || (it.status == "scheduled" && (it.start ?: Instant.EPOCH) >= now) }
            .sortedWith(compareBy({ if (it.isLive) 0 else 1 }, { it.start })).firstOrNull()?.let { e to it }
    }.minWithOrNull(compareBy({ if (it.second.isLive) 0 else 1 }, { it.second.start }))
    SportsPanel(title = null) {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow(if (next?.second?.isLive == true) "Current session" else "Next on the calendar")
            if (next != null) {
                val (weekend, session) = next
                Text(SportsRaceFormat.cardTitle(weekend.title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(session.title.takeIf { session !== weekend }, SportsRaceFormat.date(session.start)).joinToString(" · "),
                     fontSize = 13.sp, color = SportsMuted)
                listOfNotNull(weekend.venue, "Saved schedule · updates delayed".takeIf { weekend.stale || failed }).joinToString(" · ")
                    .ifEmpty { null }?.let { Text(it, fontSize = 13.sp, color = SportsMuted) }
                OutlinedButton(onClick = { navigator.open(Page.SportsRace(weekend.id)) }) { Text("Open Race Weekend") }
            } else {
                Text(if (board == null && !failed) "Loading the race calendar…" else "No upcoming weekend supplied in the current calendar coverage.",
                     fontSize = 13.sp, color = SportsMuted)
            }
        }
    }
    if (series == "motogp") {
        var standings by remember { mutableStateOf<SportsMotoGPEnrichment?>(null) }
        var standingsFailed by remember { mutableStateOf(false) }
        var retries by remember { mutableStateOf(0) }
        LaunchedEffect(retries) {
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
        SportsMotoGPStandings(standings, standingsFailed) { retries++ }
    } else {
        SportsLeagueStandings(remember(series) { SportsStandingsState(series) })
    }
}
