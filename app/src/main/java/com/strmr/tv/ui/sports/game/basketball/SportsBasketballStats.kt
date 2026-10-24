package com.strmr.tv.ui.sports.game.basketball

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsComparison
import com.strmr.tv.sports.SportsPlayerStats
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsPlayerPortrait
import com.strmr.tv.ui.sports.game.SportsTeamColors
import com.strmr.tv.ui.sports.game.charts.chartPair

/**
 * Basketball's key team statistics (Expo `SportsBasketballOverviewStats`, "Team comparison"; tvOS
 * `SportsBasketballTeamStats`): shooting as a percentage over made–attempted, then 3-pointers, free
 * throws, rebounds, assists and turnovers, home on the left in the chart colours. One stop per row.
 */
@Composable
fun SportsBasketballTeamStats(page: SportsGamePage) {
    val game = page.game
    val pair = SportsTeamColors.chartPair(game.awayTeam, game.homeTeam)
    SportsPanel("Team Comparison") {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SportsTeamLogo(game.homeTeam, 20.dp)
            Text(game.homeTeam.shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SportsTeamColors.parse(pair.home) ?: Color.White)
            Spacer(Modifier.weight(1f))
            Text(game.awayTeam.shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SportsTeamColors.parse(pair.away) ?: Color.White)
            SportsTeamLogo(game.awayTeam, 20.dp)
        }
        SportsBasketballStatRow.rows(page.detail.comparisons).forEach { row ->
            val spoken = "${row.label}, ${game.homeTeam.shortName} ${row.home}, ${game.awayTeam.shortName} ${row.away}"
            SportsReadout(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Figure(row.home, row.homeAttempts, TextAlign.Start, Modifier.weight(1f))
                    Text(row.label, fontSize = 11.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f))
                    Figure(row.away, row.awayAttempts, TextAlign.End, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Figure(value: String, attempts: String?, align: TextAlign, modifier: Modifier) {
    Column(modifier, horizontalAlignment = if (align == TextAlign.Start) Alignment.Start else Alignment.End) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        attempts?.let { Text(it, fontSize = 11.sp, color = SportsMuted) }
    }
}

/** A row of [SportsBasketballTeamStats]: each side's figure, with "39-87" under a percentage. */
data class SportsBasketballStatRow(
    val label: String,
    val away: String,
    val home: String,
    val awayAttempts: String? = null,
    val homeAttempts: String? = null,
) {
    companion object {
        private val priorities = listOf(
            Triple("Field goals", """^field goals?$""", """^field goal %|field goal percentage|fg%$"""),
            Triple("3-pointers", """three.point|three pointer|3pt""", null),
            Triple("Free throws", """^free throws?$""", null),
            Triple("Rebounds", """^(total )?rebounds$""", null),
            Triple("Assists", """^assists$""", null),
            Triple("Turnovers", """^(total )?turnovers$""", null),
        )

        /** Expo `basketballOverviewComparisons`. */
        fun rows(comparisons: List<SportsComparison>): List<SportsBasketballStatRow> = priorities.mapNotNull { (label, match, percentage) ->
            val row = comparisons.firstOrNull { SportsGameFormat.matches(match, it.label) }
            val percent = percentage?.let { pattern -> comparisons.firstOrNull { SportsGameFormat.matches(pattern, it.label) } }
            if (percent != null) {
                fun shown(value: String) = if (value.endsWith("%")) value else "$value%"
                SportsBasketballStatRow(label, shown(percent.away), shown(percent.home), row?.away, row?.home)
            } else {
                row?.let { SportsBasketballStatRow(label, it.away, it.home) }
            }
        }
    }
}

/**
 * Each side's top scorer from the box score (Expo `SportsBasketballScoringLeaders`): headshot,
 * name and "PHI · 32 PTS · 5 REB · 6 AST", home first, side by side; one stop each.
 */
@Composable
fun SportsBasketballScoringLeaders(page: SportsGamePage) {
    SportsPanel("Scoring Leaders") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            scoringLeaders(page).forEach { (team, player) ->
                SportsReadout(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SportsPlayerPortrait(player.name, player.headshotUrl, team, 34.dp)
                        Column {
                            Text(player.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val line = listOf(team.shortName) + listOf("PTS", "REB", "AST").mapNotNull { label -> player.value(label)?.let { "$it $label" } }
                            Text(line.joinToString(" · "), fontSize = 11.sp, color = SportsMuted, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

/** Players with a plain points figure, the highest per side, home first (the feed's first wins a tie). */
fun scoringLeaders(page: SportsGamePage): List<Pair<SportsTeam, SportsPlayerStats>> =
    listOf(page.game.homeTeam, page.game.awayTeam).mapNotNull { team ->
        fun points(player: SportsPlayerStats) = player.value("PTS")?.toIntOrNull() ?: -1
        page.detail.playerStats.filter { it.teamId == team.id && points(it) >= 0 }
            .fold(null as SportsPlayerStats?) { best, player -> if (best == null || points(player) > points(best)) player else best }
            ?.let { team to it }
    }
