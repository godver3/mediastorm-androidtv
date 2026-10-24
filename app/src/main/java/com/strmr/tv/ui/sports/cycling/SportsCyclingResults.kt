package com.strmr.tv.ui.sports.cycling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsCyclingFormat
import com.strmr.tv.sports.SportsCyclingRace
import com.strmr.tv.sports.SportsCyclingResult
import com.strmr.tv.sports.SportsCyclingStage
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsColumnHeading
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsNotice
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsChipRow
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsSectionHeading

/**
 * The results tabs (Expo `SportsCyclingDetailTV`'s results column): the stage's (or a team time
 * trial's team) classification, or the overall standings, with DNF / DNS / DSQ chips, 20 riders a
 * page, the leading three and the source line; beside a stage's results, the overall leaders with
 * View Overall Standings.
 */
@Composable
fun SportsCyclingResults(state: SportsCyclingPageState, race: SportsCyclingRace, stage: SportsCyclingStage, onOverall: () -> Unit) {
    val gc = stage.generalClassification
    val showsGc = !race.oneDay && state.tab == SportsCyclingTab.OVERVIEW && gc.rows.isNotEmpty()
    SportsColumns {
        SportsColumn(1.6f) { Classification(state, race, stage) }
        if (showsGc) SportsColumn(1f) {
            SportsPanel(title = null) {
                SportsClassificationLeaders(
                    label = "${if (gc.stale) "SAVED " else ""}OVERALL GC · ${stage.generalClassificationLabel ?: "After ${stage.name}"}",
                    entries = gc.rows, gapFirst = true,
                )
                OutlinedButton(onClick = onOverall, modifier = Modifier.padding(start = 10.dp, top = 6.dp)) { Text("View Overall Standings") }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Classification(state: SportsCyclingPageState, race: SportsCyclingRace, stage: SportsCyclingStage) {
    val classification = state.classification
    val overall = state.tab == SportsCyclingTab.OVERALL
    val teams = state.teamStage
    val rows = state.rows
    SportsSectionHeading(
        SportsCyclingFormat.resultsTitle(race, overall, teams),
        if (overall && !race.oneDay) "${stage.generalClassificationLabel ?: "After ${stage.name.lowercase()}"} · Overall race standings" else null,
    )
    if (state.loading && rows.isEmpty()) {
        SportsNotice("Updating results", "Loading the selected stage's published classifications.", loading = true)
    }
    if (state.delayed) {
        SportsNotice("Updates delayed", "Displayed results may be incomplete or from an earlier update.", onRetry = state::retry)
    }
    if (rows.isEmpty() && !state.loading && !state.failed) {
        val (title, message) = SportsCyclingFormat.emptyNotice(race, stage, classification)
        SportsNotice(title, message)
    }
    if (rows.isEmpty()) return
    val statuses = state.resultStatuses
    if (statuses.isNotEmpty()) {
        val chosen = remember { FocusRequester() }
        SportsChipRow(chosen) {
            val all = state.activeFilter == null
            FilterChip(selected = all, onClick = { state.filter(null) }, modifier = if (all) Modifier.focusRequester(chosen) else Modifier) {
                Text(if (teams) "All Teams" else "All Riders")
            }
            statuses.forEach { status ->
                val on = state.activeFilter == status
                FilterChip(selected = on, onClick = { state.filter(status) }, modifier = if (on) Modifier.focusRequester(chosen) else Modifier) { Text(status) }
            }
        }
    }
    if (state.pages > 1) Pager(state)
    SportsPanel(title = null, note = "${state.filtered.size} ${if (teams) "teams" else "riders"}") {
        Row(Modifier.padding(horizontal = 10.dp)) {
            SportsColumnHeading("Pos", Modifier.width(44.dp))
            SportsColumnHeading(if (teams) "Team" else "Rider / Team", Modifier.weight(1f))
            SportsColumnHeading("Time / Gap")
        }
        state.visible.forEach { ResultRow(it) }
    }
    if (!stage.cancelled && state.activeFilter == null) {
        SportsPanel(title = null) {
            SportsClassificationLeaders(
                label = "${if (classification?.stale == true) "Saved · " else ""}${if (overall && !race.oneDay) "GC Leaders" else "Leading Finishers"}",
                entries = rows.filter { it.result.isNullOrEmpty() }, gapFirst = false,
            )
        }
    }
    SportsCyclingFormat.sourceLine(classification?.source)?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
}

/** Previous / Next Riders and "2 / 8"; the pressed button hands focus to the other at either end. */
@Composable
private fun Pager(state: SportsCyclingPageState) {
    val previous = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    val page = state.currentPage
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                state.page = page - 1
                if (page - 1 == 0) runCatching { next.requestFocus() }
            },
            enabled = page > 0,
            modifier = Modifier.focusRequester(previous).focusProperties { canFocus = page > 0 },
        ) { Text("Previous Riders") }
        OutlinedButton(
            onClick = {
                state.page = page + 1
                if (page + 1 == state.pages - 1) runCatching { previous.requestFocus() }
            },
            enabled = page < state.pages - 1,
            modifier = Modifier.focusRequester(next).focusProperties { canFocus = page < state.pages - 1 },
        ) { Text("Next Riders") }
        Text("${page + 1} / ${state.pages}", fontSize = 13.sp, color = SportsMuted)
    }
}

/** A rider's line: position (the winner's in accent), name, team · country · bib, DNF…, time over gap. */
@Composable
private fun ResultRow(result: SportsCyclingResult) {
    SportsReadout {
        Row(verticalAlignment = Alignment.Top) {
            Text(result.rank?.toString() ?: "—", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                 color = if (result.rank == 1) Accent else SportsMuted, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(result.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SportsCyclingFormat.riderLine(result).ifEmpty { null }?.let {
                    Text(it, fontSize = 12.sp, color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                result.result?.ifEmpty { null }?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent) }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                result.time?.let { Text(it, fontSize = 14.sp) }
                result.gap?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
                if (result.time == null && result.gap == null) Text("—", fontSize = 12.sp, color = SportsMuted)
            }
        }
    }
}

/**
 * The top three of a classification (Expo `SportsClassificationLeaders`): reported positions
 * only, each a readout with its big position, name, team and time (the winner's) or gap.
 */
@Composable
fun SportsClassificationLeaders(label: String, entries: List<SportsCyclingResult>, gapFirst: Boolean) {
    val leaders = entries.filter { (it.rank ?: 0) in 1..3 }.sortedBy { it.rank }
    if (leaders.isEmpty()) return
    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 1.sp,
         modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    leaders.forEach { leader ->
        SportsReadout {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${leader.rank}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(leader.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    leader.team?.ifEmpty { null }?.let { Text(it, fontSize = 11.sp, color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                SportsCyclingFormat.leaderValue(leader, gapFirst)?.let {
                    Text(it, fontSize = 13.sp, color = Color.White, textAlign = TextAlign.End)
                }
            }
        }
    }
}
