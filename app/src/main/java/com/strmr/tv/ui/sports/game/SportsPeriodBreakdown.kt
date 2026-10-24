package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsPeriodScore
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo

/**
 * Points per period for both sides with the totals (Expo `SportsPeriodBreakdown`): goals by period
 * for hockey, points by set for volleyball, innings for baseball. One stop for the remote; the
 * columns narrow to fit long games (extra innings, overtimes).
 */
@Composable
fun SportsPeriodBreakdown(page: SportsGamePage) {
    val periods = page.detail.periods
    val measure = rememberSportsTableMeasure()
    // The team column fits the longest short name ("C. Rodesch") after a logo and its gap.
    val names = measure.widest(page.teams.map { it.shortName }, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    val teamWidth = maxOf(TeamWidth, names + 34.dp)
    SportsPanel(title(page.kind)) {
        SportsReadout {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cell = CellWidths.firstOrNull { teamWidth + it * periods.size + TotalWidth <= maxWidth } ?: CellWidths.last()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell("TEAM", teamWidth, SportsMuted, size = 11, align = TextAlign.Start)
                        periods.forEach { Cell(it.label, cell, SportsMuted, size = 11) }
                        Cell("TOTAL", TotalWidth, SportsMuted, size = 11)
                    }
                    page.teams.forEach { team ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(teamWidth), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SportsTeamLogo(team, 18.dp)
                                Text(team.shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            periods.forEach { Cell(score(page, it, team), cell) }
                            Cell(team.score ?: "–", TotalWidth, bold = true)
                        }
                    }
                }
            }
        }
    }
}

private fun title(kind: SportsKind) = when (kind) {
    SportsKind.HOCKEY -> "Goals by Period"
    SportsKind.VOLLEYBALL -> "Points by Set"
    SportsKind.BASEBALL, SportsKind.SOFTBALL -> "Innings"
    SportsKind.TENNIS -> "Sets"
    SportsKind.FOOTBALL -> "Scoring by Quarter"
    else -> "Scoring by Period"
}

private fun score(page: SportsGamePage, period: SportsPeriodScore, team: SportsTeam): String =
    (if (team.id == page.game.homeTeam.id) period.home else period.away).ifEmpty { "–" }

@Composable
private fun Cell(text: String, width: Dp, color: Color = Color.White, size: Int = 14, bold: Boolean = false, align: TextAlign = TextAlign.Center) {
    Text(
        text, fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align,
        maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.width(width),
    )
}

private val TeamWidth = 65.dp
private val TotalWidth = 55.dp
private val CellWidths = listOf(42.dp, 32.dp, 25.dp)
