package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsComparison
import com.strmr.tv.sports.SportsPlayerStats
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import kotlinx.coroutines.delay

/**
 * The Box Score / Match Stats tab (Expo `SportsPlayerStats` beside `StatsPanel`): pick a side,
 * then its players' game stats in one table per category (Passing, Rushing…), with every team
 * statistic alongside. When either side's tables need more than two thirds of the width (NBA's
 * dozen columns), the team statistics go underneath; football's split by share (Expo
 * `FootballComparison`). Opened on a player ([target], MLB's Overview), it shows their side with
 * their row marked "Selected Player" and focused.
 */
@Composable
fun SportsBoxScore(page: SportsGamePage, target: SportsPlayerTarget? = null) {
    var teamId by remember { mutableStateOf<String?>(null) }
    val targetFocus = remember { FocusRequester() }
    LaunchedEffect(target) {
        target ?: return@LaunchedEffect
        teamId = target.teamId
        // Once the side's tables are drawn, the player's row takes focus.
        delay(150)
        runCatching { targetFocus.requestFocus() }
    }
    val marked = target?.let { it to targetFocus }
    val stats = page.detail.playerStats
    val teams = page.teams.filter { team -> stats.any { it.teamId == team.id } }
    val chosen = teams.firstOrNull { it.id == teamId } ?: teams.firstOrNull()
    val measure = rememberSportsTableMeasure()
    val sides = remember(stats, teams, measure) {
        teams.associate { team -> team.id to SportsBoxScoreTable.tables(team.id, stats).map { it to it.columnWidths(measure) } }
    }
    // Either side's widest table decides, so the layout holds when the side changes.
    val wide = sides.values.flatten().maxOfOrNull { (_, widths) -> PlayerColumn + widths.fold(0.dp) { sum, width -> sum + width } }
        ?.let { it > WideTable } ?: false
    val comparisons = page.detail.comparisons
    when {
        teams.isEmpty() && comparisons.isEmpty() -> SportsPanel(null) {
            SportsReadout { Text("No box score in the loaded feed.", fontSize = 14.sp, color = SportsMuted) }
        }
        chosen == null || comparisons.isEmpty() || wide -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (chosen != null) Players(teams, chosen, sides[chosen.id].orEmpty(), marked) { teamId = it }
            if (comparisons.isNotEmpty()) Comparison(page, comparisons)
        }
        else -> SportsColumns {
            SportsColumn(2.15f) { Players(teams, chosen, sides[chosen.id].orEmpty(), marked) { teamId = it } }
            SportsColumn(1f) { Comparison(page, comparisons) }
        }
    }
}

/** Every team statistic, then hockey's and basketball's shooting panel (Expo `ShootingComparison`). */
@Composable
private fun Comparison(page: SportsGamePage, rows: List<SportsComparison>) {
    SportsTeamComparison(page, rows, "Team Comparison", if (page.isFootballLeague) SportsComparisonStyle.SHARE else SportsComparisonStyle.CENTRE)
    if (SportsShooting.rows(page).isNotEmpty()) SportsShootingComparison(page)
}

/** The player the page opened the Box Score on, and the focus their row takes. */
private typealias Marked = Pair<SportsPlayerTarget, FocusRequester>

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Players(
    teams: List<SportsTeam>,
    chosen: SportsTeam,
    tables: List<Pair<SportsBoxScoreTable, List<Dp>>>,
    marked: Marked?,
    onChoose: (String) -> Unit,
) {
    val chipFocus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SportsChipRow(chipFocus) {
            teams.forEach { team ->
                FilterChip(
                    selected = team.id == chosen.id, onClick = { onChoose(team.id) },
                    leadingIcon = { SportsTeamLogo(team, 18.dp) },
                    modifier = if (team.id == chosen.id) Modifier.focusRequester(chipFocus) else Modifier,
                ) { Text(team.name, maxLines = 1) }
            }
        }
        tables.forEach { (table, widths) -> Table(table, widths, chosen, marked) }
    }
}

@Composable
private fun Table(table: SportsBoxScoreTable, widths: List<Dp>, team: SportsTeam, marked: Marked?) {
    SportsPanel(table.category.ifEmpty { "Players" }) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Player", fontSize = 11.sp, color = SportsMuted, modifier = Modifier.weight(1f))
            table.columns.forEachIndexed { index, label ->
                Text(SportsBoxScoreTable.heading(label), fontSize = 11.sp, color = SportsMuted, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(widths[index]))
            }
        }
        table.rows.forEach { row ->
            val focus = marked?.takeIf { it.first == SportsPlayerTarget(team.id, row.id, table.category) }?.second
            val selected = focus != null
            SportsReadout(if (focus != null) Modifier.focusRequester(focus) else Modifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SportsPlayerPortrait(row.name, row.headshotUrl, team, 22.dp)
                        Text(row.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        row.jersey?.let { Text("#$it", fontSize = 11.sp, color = SportsMuted) }
                        if (selected) Text("Selected Player", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Accent)
                    }
                    table.columns.forEachIndexed { index, label ->
                        Text(row.value(label) ?: "—", fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(widths[index]))
                    }
                }
            }
        }
    }
}

/**
 * One category of a side's box score (Expo `SportsPlayerStats` per category): its players with
 * stats, and the columns they share, in the feed's order.
 */
data class SportsBoxScoreTable(val category: String, val columns: List<String>, val rows: List<SportsPlayerStats>) {
    /** Each column's width for its heading and figures. */
    fun columnWidths(measure: SportsTableMeasure): List<Dp> =
        columns.map { label -> measure.width(heading(label), rows.mapNotNull { it.value(label) }) }

    companion object {
        fun tables(teamId: String, stats: List<SportsPlayerStats>): List<SportsBoxScoreTable> =
            stats.filter { it.teamId == teamId && it.stats.isNotEmpty() }
                .groupBy { it.category } // keeps the feed's order of categories and players
                .map { (category, players) ->
                    SportsBoxScoreTable(category, players.flatMap { player -> player.stats.map { it.label } }.distinct(), players)
                }

        /** A column's heading as Expo shows it ("SACK / YDS"). */
        fun heading(label: String): String = if (label == "SACK-YDS") "SACK / YDS" else label
    }
}

/** The player column: headshot and name. */
private val PlayerColumn = 180.dp

/** Wider than this (about two thirds of the page), the team comparison goes under the tables. */
private val WideTable = 520.dp
