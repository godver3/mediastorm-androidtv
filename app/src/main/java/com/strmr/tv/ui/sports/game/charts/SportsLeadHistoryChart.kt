package com.strmr.tv.ui.sports.game.charts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The lead over the game (Expo `SportsLeadHistory`, tvOS `SportsLeadHistoryChart`): the margin by
 * elapsed time, the away side's lead above zero filled in its colour, the home side's below in
 * its own, the line coloured by who leads, the periods along the bottom and the latest margin
 * marked. One stop for the remote.
 */
@Composable
fun SportsLeadHistoryChart(page: SportsGamePage, series: SportsLeadSeries) {
    val game = page.game
    val pair = SportsTeamColors.chartPair(game.awayTeam, game.homeTeam)
    val away = SportsTeamColors.parse(pair.away) ?: Color.Cyan
    val home = SportsTeamColors.parse(pair.home) ?: Color.Yellow
    val extent = series.extent
    val spoken = "Lead history. ${game.awayTeam.name} above zero, ${game.homeTeam.name} below zero. " +
        "Score ${game.awayTeam.score.orEmpty()} to ${game.homeTeam.score.orEmpty()}."
    val ticks = listOf(-extent, -extent / 2, 0.0, extent / 2, extent).map { it to if (it == 0.0) "Tie" else "${abs(it).toInt()}" }
    SportsPanel("Lead History", note = "${game.awayTeam.shortName} leads above zero · ${game.homeTeam.shortName} below") {
        SportsReadout(Modifier.semantics { contentDescription = spoken }) {
            SportsChart(185.dp, series.duration, -extent, extent, ticks, series.periods) { area, _ ->
                val curve = series.crossings
                // One continuous area per side, to the axis.
                for ((side, color) in listOf(true to away, false to home)) {
                    val fill = Path()
                    fill.moveTo(area.x(curve.first().elapsed), area.y(0.0))
                    curve.forEach { fill.lineTo(area.x(it.elapsed), area.y(if (side) max(it.lead, 0.0) else min(it.lead, 0.0))) }
                    fill.lineTo(area.x(curve.last().elapsed), area.y(0.0))
                    fill.close()
                    drawPath(fill, color.copy(alpha = 0.2f))
                }
                drawLine(SportsMuted, area.at(0.0, 0.0), Offset(area.plot.right, area.y(0.0)), 1.5.dp.toPx())
                series.runs.forEach { run ->
                    chartLine(linearPath(run.points.map { area.at(it.elapsed, it.lead) }), run.away?.let { if (it) away else home } ?: SportsMuted)
                }
                val last = series.points.last()
                drawCircle(Color.White, 5.5.dp.toPx(), area.at(last.elapsed, last.lead))
            }
            Text(summary(page, series), fontSize = 12.sp, color = SportsMuted)
        }
    }
}

/** "PHI +8 · Final · Curve connects reported scores". */
private fun summary(page: SportsGamePage, series: SportsLeadSeries): String {
    val game = page.game
    val lead = series.latest
    val margin = if (lead == 0.0) "Tied" else "${if (lead > 0) game.awayTeam.shortName else game.homeTeam.shortName} +${abs(lead).toInt()}"
    val state = if (page.lifecycle == SportsLifecycle.FINAL) "Final" else "Latest score"
    return listOf(margin, state, "Curve connects reported scores").joinToString(" · ")
}
