package com.strmr.tv.ui.sports.standings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLeagueTable
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.sports.SportsStandings
import com.strmr.tv.sports.SportsTableRow
import com.strmr.tv.sports.parseInstant
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.PickerDialog
import com.strmr.tv.ui.sports.SportsColumnHeading
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsNotice
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsSectionHeading

/**
 * A league's standings (Expo `SportsLeagueStandings`): the season line, a table picker when
 * there are several (conferences, divisions), the leading entry, then the table with four
 * columns chosen per kind of league; a row opens to every supplied statistic and, in a league's
 * overview, a button for that team's games. Teams with games in the selection are marked.
 */
@Composable
fun SportsLeagueStandings(
    state: SportsStandingsState,
    fixtureTeamIds: Set<String> = emptySet(),
    selectedTeamId: String? = null,
    onTeamSelect: ((SportsTableRow) -> Unit)? = null,
) {
    LaunchedEffect(state.league, state.retries) { state.poll() }
    var picking by remember { mutableStateOf(false) }
    val data = state.standings
    val table = state.table
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SportsSectionHeading("Standings")
        if (data == null && !state.failed) SportsNotice("Loading standings", "Fetching the provider's league table.", loading = true)
        if (state.failed || data?.delayed == true) {
            SportsNotice(
                if (data?.groups.isNullOrEmpty()) "Standings unavailable" else "Standings updates delayed",
                data?.reason?.ifEmpty { null } ?: "Any displayed table is from the last successful update.",
                onRetry = state::retry,
            )
        }
        if (table != null) {
            Column {
                Text("${table.seasonLabel} · Provider table season ${table.season}", fontSize = 12.sp, color = SportsMuted)
                Text("Season tables are independent of the selected event date.", fontSize = 12.sp, color = SportsMuted)
            }
            if (data!!.groups.size > 1) {
                OutlinedButton(onClick = { picking = true }) {
                    Text("${SportsStandings.groupLabel(state.league)}: ${table.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Text(table.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            SportsStandings.leader(table)?.let { Leader(it, table) }
            Table(state, table, fixtureTeamIds, selectedTeamId, onTeamSelect)
            Text(
                if (fixtureTeamIds.isNotEmpty()) "Marked teams have games in this selection. Select a row for full statistics."
                else "Select a row for full supplied statistics.",
                fontSize = 12.sp, color = SportsMuted,
            )
        }
        data?.updatedAt?.let(::parseInstant)?.let {
            Text("${data.source.ifEmpty { "ESPN" }} · Checked ${SportsRaceFormat.date(it)}", fontSize = 12.sp, color = SportsMuted)
        }
    }
    val groups = data?.groups.orEmpty()
    if (picking && groups.isNotEmpty()) {
        PickerDialog(
            title = SportsStandings.groupLabel(state.league), options = groups.map { it.title },
            selected = groups.indexOfFirst { it.id == table?.id }, onPick = { state.choose(groups[it].id) }, onDismiss = { picking = false },
        )
    }
}

/** The leading entry (Expo's leader box): "LEADING ENTRY · SEED 1", the name, points and record. */
@Composable
private fun Leader(row: SportsTableRow, table: SportsLeagueTable) {
    SportsReadout(selected = true) {
        Text(SportsStandings.leaderEyebrow(row), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent, letterSpacing = 1.sp)
        Text(row.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        SportsStandings.leaderSummary(row, table).ifEmpty { null }?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Table(
    state: SportsStandingsState,
    table: SportsLeagueTable,
    fixtureTeamIds: Set<String>,
    selectedTeamId: String?,
    onTeamSelect: ((SportsTableRow) -> Unit)?,
) {
    val columns = SportsStandings.columns(state.league, table)
    SportsPanel(title = null) {
        Row(Modifier.padding(horizontal = 10.dp)) {
            SportsColumnHeading("Team / Competitor", Modifier.weight(1f))
            columns.forEach {
                Box(Modifier.width(columnWidth(it.key)), contentAlignment = Alignment.CenterEnd) { SportsColumnHeading(it.label) }
            }
        }
        table.rows.forEach { row ->
            val marked = row.id == selectedTeamId || row.id in fixtureTeamIds
            val open = state.expandedId == row.id
            SportsReadout(selected = open, onClick = { state.expandedId = if (open) null else row.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(18.dp).background(if (marked) Accent else Color.Transparent))
                    Text(row.name, fontSize = 14.sp, fontWeight = if (marked) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
                         overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    columns.forEach {
                        Text(SportsStandings.value(row, it.key), fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 1,
                             modifier = Modifier.width(columnWidth(it.key)))
                    }
                }
                if (open) {
                    FlowRow(Modifier.padding(start = 11.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        table.columns.forEach { column ->
                            Text(buildAnnotatedString {
                                withStyle(SpanStyle(color = SportsMuted)) { append("${column.label}: ") }
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(SportsStandings.value(row, column.key)) }
                            }, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (open && onTeamSelect != null && row.kind == "team") {
                OutlinedButton(onClick = { onTeamSelect(row) }, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)) {
                    Text("Show ${row.abbreviation?.ifEmpty { null } ?: row.name} Games")
                }
            }
        }
    }
}

/** A record ("8-2-2, 18 PTS") needs more room than a seed or a percentage. */
private fun columnWidth(key: String) = if (key == "overall") 110.dp else 72.dp
