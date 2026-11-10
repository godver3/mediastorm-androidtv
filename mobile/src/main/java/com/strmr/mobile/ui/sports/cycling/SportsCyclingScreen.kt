package com.strmr.mobile.ui.sports.cycling

import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.mobile.ui.sports.LocalSportsCompact
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import com.strmr.mobile.ui.sports.*
import com.strmr.mobile.ui.sports.SportsChip
import com.strmr.tv.sports.*
import com.strmr.tv.ui.sports.*
import com.strmr.tv.ui.sports.cycling.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.sports.SportsCycling
import com.strmr.tv.sports.SportsCyclingFormat
import com.strmr.tv.sports.SportsCyclingRace
import com.strmr.tv.sports.SportsCyclingStage
import com.strmr.mobile.ui.theme.Accent
import com.strmr.mobile.ui.sports.PageMargin
import com.strmr.mobile.ui.sports.PickerDialog
import com.strmr.mobile.ui.sports.SportsMuted
import com.strmr.mobile.ui.sports.SportsNotice
import com.strmr.mobile.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsSpoilers
import com.strmr.mobile.ui.sports.SportsStreamPicker
import com.strmr.tv.ui.sports.SportsWatchTarget
import com.strmr.mobile.ui.sports.game.SportsChipRow

/**
 * A cycling race's page (Expo `app/sports/cycling/[raceId].tsx` with `SportsCyclingDetailTV`):
 * the race and the shown stage's line, Find Streams, the stage picker and Hide Scores, then
 * Stage Overview (results beside the overall leaders), Overall GC and Route. A dates-only race
 * shows its published schedule; Hide Scores leaves the stage's facts only.
 */
@Composable
fun SportsCyclingScreen(raceId: String, stageId: String?) {
    val state = remember(raceId) { SportsCyclingPageState(raceId, stageId) }
    val spoilers = SportsSpoilers.hidden
    val firstFocus = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var watching by remember { mutableStateOf<SportsWatchTarget?>(null) }
    var choosingStage by remember { mutableStateOf(false) }
    var focusTab by remember { mutableStateOf(false) }

    LaunchedEffect(state.retries) { state.loadRace() }
    LaunchedEffect(state.stage?.id, state.race?.scheduleOnly, state.retries) { state.pollStage() }
    val ready = state.race != null
    LaunchedEffect(ready) { if (ready) runCatching { firstFocus.requestFocus() } }
    LaunchedEffect(state.tab, focusTab) {
        if (!focusTab) return@LaunchedEffect
        listState.scrollToItem(0)
        runCatching { tabFocus.requestFocus() }
        focusTab = false
    }

    PageScaffold(state.race?.name ?: "Cycling", showBack = true) { padding ->
        BoxWithConstraints {
            CompositionLocalProvider(LocalSportsCompact provides (maxWidth < 600.dp)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val race = state.race
                when {
                    race != null -> {
                        item { Header(state, race, spoilers, firstFocus, tabFocus, onWatch = { watching = it }, onStages = { choosingStage = true }) }
                        val stage = state.detail
                        when {
                            stage == null -> item { Text("No stages are available.", color = SportsMuted) }
                            spoilers && !race.scheduleOnly -> item { SpoilerSummary(race, stage) }
                            else -> {
                                if (race.scheduleOnly) item { ScheduleNotice(race) }
                                when (state.tab) {
                                    SportsCyclingTab.ROUTE -> item { SportsCyclingRoute(state, race, stage) }
                                    else -> if (!race.scheduleOnly) item {
                                        SportsCyclingResults(state, race, stage) {
                                            state.show(SportsCyclingTab.OVERALL)
                                            focusTab = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    state.missing != null -> item {
                        SportsNotice("Cycling unavailable", state.missing.orEmpty(), Modifier.padding(top = 60.dp), onRetry = state::retry)
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
    val race = state.race
    if (choosingStage && race != null) {
        PickerDialog(
            title = if (race.championship) "Race" else "Stage", options = race.stages.map(SportsCyclingFormat::stageOption),
            selected = race.stages.indexOfFirst { it.id == state.stage?.id },
            onPick = { state.select(race.stages[it].id) }, onDismiss = { choosingStage = false },
        )
    }
}

/**
 * The page's top (Expo's editorial header and context row): "CYCLING", the race, the stage's
 * date, route, distance and terrain with its state, then Find Streams, the stage picker and Hide
 * Scores, and the tabs (none while scores are hidden). One block so the title stays in view.
 */
@Composable
private fun Header(
    state: SportsCyclingPageState,
    race: SportsCyclingRace,
    spoilers: Boolean,
    firstFocus: FocusRequester,
    tabFocus: FocusRequester,
    onWatch: (SportsWatchTarget) -> Unit,
    onStages: () -> Unit,
) {
    val stage = state.detail
    val watch = state.watchTarget
    val choosesStage = !race.oneDay && race.stages.size > 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val eyebrow = listOfNotNull("CYCLING", "RECORDED".takeIf { race.captured }, "UPDATES DELAYED".takeIf { state.delayed })
        Text(eyebrow.joinToString(" · "), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted)
        Text(race.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (stage != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(SportsCyclingFormat.stageLine(stage), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                     maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!spoilers) Text(SportsCyclingFormat.stageState(race, stage, state.classification), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
            }
        }
        val tabsShown = !spoilers || race.scheduleOnly
        // Down from each control lands on the shown tab (a plain search skipped the chip row).
        val toTabs = Modifier.focusProperties { if (tabsShown) down = tabFocus }
        SportsChipRow(null, Modifier.padding(top = 10.dp)) {
            if (watch != null) {
                Button(onClick = { onWatch(watch) }, modifier = toTabs.focusRequester(firstFocus)) { Text("Find Streams") }
            }
            if (choosesStage) {
                OutlinedButton(onClick = onStages, modifier = toTabs.then(if (watch == null) Modifier.focusRequester(firstFocus) else Modifier)) {
                    Text("${if (race.championship) "Race" else "Stage"}: ${state.stage?.name ?: "Choose"}")
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            OutlinedButton(
                onClick = { SportsSpoilers.toggle() },
                modifier = toTabs.then(if (watch == null && !choosesStage) Modifier.focusRequester(firstFocus) else Modifier),
            ) { Text(if (spoilers) "Show Scores" else "Hide Scores") }
        }
        if (tabsShown) {
            SportsChipRow(tabFocus, Modifier.padding(top = 4.dp)) {
                state.tabs.forEach { tab ->
                    SportsChip(
                        selected = state.tab == tab, onClick = { state.show(tab) },
                        modifier = if (state.tab == tab) Modifier.focusRequester(tabFocus) else Modifier,
                    ) { Text(tabLabel(tab, race)) }
                }
            }
        }
    }
}

private fun tabLabel(tab: SportsCyclingTab, race: SportsCyclingRace): String = when (tab) {
    SportsCyclingTab.OVERVIEW -> SportsCyclingFormat.overviewTab(race)
    SportsCyclingTab.OVERALL -> "Overall GC"
    SportsCyclingTab.ROUTE -> if (race.calendarOnly) "Source" else "Route"
}

/** A dates-only race (Expo's published-schedule notice): its dates, country and what the feed can't say. */
@Composable
private fun ScheduleNotice(race: SportsCyclingRace) {
    SportsPanel(title = race.name) {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOfNotNull(
                "${SportsCycling.dateRange(race)} · Start time TBC",
                race.country?.ifEmpty { null },
                "Use Find Streams to search your available sources. Broadcast availability is not confirmed.",
                "${race.source.provider.ifEmpty { "Organizer" }} · Checked ${SportsCycling.date(race.source.observedAt)}. " +
                    "Dates may change. Live timing and results are not supplied.",
            ).forEach { Text(it, fontSize = 13.sp, color = SportsMuted) }
        }
    }
}

/** Hide Scores (Expo's spoiler view): the race and stage facts, no classification. */
@Composable
private fun SpoilerSummary(race: SportsCyclingRace, shown: SportsCyclingStage) {
    SportsPanel(title = race.name) {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOfNotNull(
                race.country?.ifEmpty { null },
                shown.name,
                shown.route.ifEmpty { null },
                listOfNotNull(shown.distance, shown.terrain).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            ).forEach { Text(it, fontSize = 13.sp, color = SportsMuted) }
            Text("Stage results and classification hidden", fontSize = 13.sp)
        }
    }
}
