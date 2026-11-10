package com.strmr.mobile.ui.sports.race

import com.strmr.mobile.ui.sports.game.SportsColumn
import com.strmr.mobile.ui.sports.game.SportsColumns
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.mobile.ui.sports.LocalSportsCompact
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import com.strmr.mobile.ui.sports.*
import com.strmr.tv.sports.*
import com.strmr.tv.ui.sports.*
import com.strmr.tv.ui.sports.race.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.sports.SportsF1Driver
import com.strmr.tv.sports.SportsF1Timing
import com.strmr.tv.sports.SportsRaceEvent
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.mobile.ui.sports.PageMargin
import com.strmr.mobile.ui.sports.PickerDialog
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.mobile.ui.sports.SportsMuted
import com.strmr.mobile.ui.sports.SportsNotice
import com.strmr.mobile.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsSpoilers
import com.strmr.mobile.ui.sports.SportsStreamPicker
import com.strmr.tv.ui.sports.SportsWatchTarget

/**
 * A race weekend's page (Expo `app/sports/race/[eventId].tsx` with `SportsRaceDetailTV`): the
 * header ([SportsRaceHeader]), then Overview (the classification's top eight beside the driver
 * card, the comparison and the circuit guide), Timing (every entry, with F1 archive telemetry)
 * or MotoGP's Championship, and F1's session insights under the first two. Hide Scores leaves
 * the schedule and the circuit. The session reloads every 30 s, every 10 min once final.
 */
@Composable
fun SportsRaceScreen(weekendId: String, sessionId: String?) {
    val state = remember(weekendId) { SportsRacePageState(weekendId, sessionId) }
    val spoilers = SportsSpoilers.hidden
    val firstFocus = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // View All swaps itself out for Timing's rows: the remote goes to the tab it opened.
    var focusTab by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf<SportsWatchTarget?>(null) }
    var choosingSession by remember { mutableStateOf(false) }
    val championship = state.tab == SportsRaceTab.CHAMPIONSHIP
    val archive = state.timingArchive
    val telemetry = remember(archive) { archive?.let(SportsF1Timing::drivers).orEmpty() }

    LaunchedEffect(state.retries) { state.loadWeekend() }
    LaunchedEffect(state.session?.id, state.retries, championship) { if (!championship) state.pollSession() }
    LaunchedEffect(state.detail?.id, state.detail?.status) { state.loadArchive() }
    LaunchedEffect(state.weekend?.id, state.retries) { state.loadGuide() }
    LaunchedEffect(championship, state.retries) { if (championship) state.pollStandings() }
    val ready = state.weekend != null
    LaunchedEffect(ready) { if (ready) runCatching { firstFocus.requestFocus() } }
    LaunchedEffect(state.tab, focusTab) {
        if (!focusTab) return@LaunchedEffect
        listState.scrollToItem(0)
        withFrameNanos { }
        runCatching { tabFocus.requestFocus() }
        focusTab = false
    }

    PageScaffold(state.weekend?.title?.trim() ?: "Race Weekend", showBack = true) { padding ->
        BoxWithConstraints {
            CompositionLocalProvider(LocalSportsCompact provides (maxWidth < 600.dp)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val weekend = state.weekend
                when {
                    weekend != null -> {
                        item {
                            SportsRaceHeader(
                                state, weekend, SportsHub.state.leagueName(weekend.league), spoilers, firstFocus, tabFocus,
                                onWatch = { watching = it }, onSessions = { choosingSession = true },
                            )
                        }
                        when {
                            spoilers -> item { SpoilerSummary(state, weekend) }
                            championship -> item { SportsMotoGPStandings(state.standings, state.standingsFailed, state::retry) }
                            state.tab == SportsRaceTab.TIMING -> item { SportsRaceSessionResults(state, telemetry, overview = false) }
                            else -> item {
                                Overview(state, weekend, telemetry) {
                                    state.tab = SportsRaceTab.TIMING
                                    focusTab = true
                                }
                            }
                        }
                        if (!spoilers && !championship && archive != null) {
                            item { SportsF1Insights(archive, state.competitor?.let { it.number ?: it.id }) }
                        }
                    }
                    state.missing != null -> item {
                        SportsNotice("Race weekend unavailable", state.missing.orEmpty(), Modifier.padding(top = 60.dp), onRetry = state::retry)
                    }
                    else -> item {
                        Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    }
                
            }
            }
        }
    }
    }

    watching?.let { target ->
        SportsStreamPicker(target, onPick = { match ->
            watching = null
            PlaybackCoordinator.playLive(match.channel, target.title)
        }, onDismiss = { watching = null })
    }
    val weekend = state.weekend
    if (choosingSession && weekend != null) {
        val sessions = weekend.sessions
        PickerDialog(
            title = "Session", options = sessions.map(SportsRaceFormat::sessionOption),
            selected = sessions.indexOfFirst { it.id == state.session?.id },
            onPick = { state.select(sessions[it].id) }, onDismiss = { choosingSession = false },
        )
    }
}

/**
 * Overview: the session's results beside the selected entry's card, the comparison and the
 * circuit; the results take the width when there is none of those (entries not out yet, no
 * circuit guide).
 */
@Composable
private fun Overview(state: SportsRacePageState, weekend: SportsRaceEvent, telemetry: List<SportsF1Driver>, onViewAll: () -> Unit) {
    val detail = state.detail
    val competitor = state.competitor
    if (competitor == null && weekend.circuit == null && !weekend.riders) {
        SportsRaceSessionResults(state, telemetry, overview = true, onViewAll = onViewAll)
        return
    }
    // Beside each other on a tablet; stacked on a phone.
    SportsColumns {
        SportsColumn(1.2f) { SportsRaceSessionResults(state, telemetry, overview = true, onViewAll = onViewAll) }
        SportsColumn(1f) {
            if (detail != null && competitor != null) {
                SportsRaceDriverCard(competitor)
                SportsRaceComparison(detail.participants, competitor, detail)
            }
            CircuitGuide(state, weekend)
        }
    }
}

/** F1's reference outline when the weekend has one, else MotoGP's organizer guide. */
@Composable
private fun CircuitGuide(state: SportsRacePageState, weekend: SportsRaceEvent) {
    val circuit = weekend.circuit
    if (circuit != null) SportsCircuitMap(circuit)
    else if (weekend.riders) {
        val venue = weekend.venue ?: weekend.subEvents.firstNotNullOfOrNull { it.venue }
        SportsMotoGPGuide(state.guide, state.guideFailed, venue, state::retry)
    }
}

/**
 * Hide Scores: what was known before the start (Expo's spoiler view: place, time and where it's
 * on, the shown session's channels first), then the circuit.
 */
@Composable
private fun SpoilerSummary(state: SportsRacePageState, weekend: SportsRaceEvent) {
    val broadcasts = state.detail?.broadcasts?.ifEmpty { null } ?: weekend.broadcasts.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SportsPanel(title = weekend.title.trim()) {
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOfNotNull(
                    (state.detail ?: weekend).place(weekend),
                    weekend.start?.let { SportsRaceFormat.date(it) },
                    broadcasts.joinToString(" · ").ifEmpty { null },
                ).forEach { Text(it, fontSize = 13.sp, color = SportsMuted) }
                Text("Session timing and results hidden", fontSize = 13.sp)
            }
        }
        CircuitGuide(state, weekend)
    }
}
