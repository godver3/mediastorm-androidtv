package com.strmr.tv.ui.sports.game.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors

/**
 * ESPN's win probability over the game (Expo `SportsWinProbability`, tvOS
 * `SportsWinProbabilityChart`): each side's chance in its colour (home, and away as the rest), a
 * dashed 50% rule, the periods along the bottom and the latest estimate marked and labelled.
 * One stop for the remote.
 */
@Composable
fun SportsWinProbabilityChart(page: SportsGamePage, series: SportsWinProbabilitySeries) {
    val game = page.game
    val pair = SportsTeamColors.chartPair(game.awayTeam, game.homeTeam)
    val home = SportsTeamColors.parse(pair.home) ?: Color.Yellow
    val away = SportsTeamColors.parse(pair.away) ?: Color.Cyan
    val latest = series.latest
    val spoken = "ESPN win probability. ${game.homeTeam.name} ${sportsPercent(latest.home)}. ${game.awayTeam.name} ${sportsPercent(1 - latest.home)}."
    SportsPanel("Win Probability") {
        SportsReadout(Modifier.semantics { contentDescription = spoken }) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("${game.homeTeam.shortName} ${sportsPercent(latest.home)}", color = home, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${game.awayTeam.shortName} ${sportsPercent(1 - latest.home)}", color = away, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            SportsChart(
                170.dp, series.duration, 0.0, 1.0,
                listOf(0.0 to "0%", 0.5 to "50%", 1.0 to "100%"), series.periods,
            ) { area, measurer ->
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                drawLine(SportsMuted, area.at(0.0, 0.5), Offset(area.plot.right, area.y(0.5)), 1.5.dp.toPx(), pathEffect = dash)
                chartLine(monotonePath(series.points.map { area.at(it.elapsed, 1 - it.home) }), away)
                chartLine(monotonePath(series.points.map { area.at(it.elapsed, it.home) }), home)
                val point = area.at(latest.elapsed, latest.home)
                drawCircle(home, 5.dp.toPx(), point)
                val label = measurer.measure(sportsPercent(latest.home), TextStyle(color = home, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                val above = latest.home <= 0.75
                val top = if (above) point.y - 8.dp.toPx() - label.size.height else point.y + 8.dp.toPx()
                drawText(label, topLeft = Offset((point.x - label.size.width).coerceAtLeast(area.plot.left), top))
            }
            val at = listOf(latest.periodLabel, latest.clock).filter { it.isNotEmpty() }.joinToString(" ")
            Text("ESPN model · $at · Latest available estimate", fontSize = 12.sp, color = SportsMuted)
        }
    }
}
