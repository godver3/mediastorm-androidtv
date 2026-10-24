package com.strmr.tv.ui.sports.game.soccer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsComparison
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsAvailabilityNotice
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsMatchInfo
import com.strmr.tv.ui.sports.game.SportsPregamePreview
import com.strmr.tv.ui.sports.game.SportsTeamColors
import com.strmr.tv.ui.sports.game.SportsTeamStats
import com.strmr.tv.ui.sports.game.availabilityApplies
import com.strmr.tv.ui.sports.game.hasMatchNotes
import com.strmr.tv.ui.sports.game.matchInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Overview of soccer matches (Expo `SportsSportOverview`'s soccer branch, tvOS
 * `SportsSoccerOverview`): before kick-off the published formations on the pitch; once under way
 * the goals and latest event and the match stats, beside the formations (pitch in Lineups) and the
 * match information. A match not under way opens with the availability notice; a scheduled one
 * ends with the matchup preview.
 */
@Composable
fun SportsSoccerOverview(page: SportsGamePage) {
    val scheduled = page.lifecycle == SportsLifecycle.SCHEDULED
    val left = (if (scheduled) listOf(Panel.AVAILABILITY, Panel.PITCH) else listOf(Panel.AVAILABILITY, Panel.TIMELINE, Panel.STATS))
        .filter { it.shows(page) }
    val right = (if (scheduled) listOf(Panel.INFO) else listOf(Panel.FORMATIONS, Panel.INFO)).filter { it.shows(page) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (left.isNotEmpty() || right.isNotEmpty()) {
            SportsColumns(fullHeight = true) {
                if (left.isNotEmpty()) SportsColumn(1.2f) { left.forEach { it.Content(page) } }
                if (right.isNotEmpty()) SportsColumn(1f) { right.forEach { it.Content(page) } }
            }
        }
        if (scheduled) SportsPregamePreview(page, page.detail.pregame)
    }
}

private enum class Panel {
    AVAILABILITY, PITCH, TIMELINE, STATS, FORMATIONS, INFO;

    fun shows(page: SportsGamePage): Boolean = when (this) {
        AVAILABILITY -> page.availabilityApplies
        PITCH, FORMATIONS -> page.detail.lineups.isNotEmpty()
        TIMELINE -> SportsSoccerTimeline.latest(page) != null
        STATS -> page.lifecycle != SportsLifecycle.SCHEDULED && page.detail.comparisons.isNotEmpty()
        INFO -> page.matchInfo.isNotEmpty() || page.hasMatchNotes
    }

    @Composable
    fun Content(page: SportsGamePage) {
        when (this) {
            AVAILABILITY -> SportsAvailabilityNotice(page)
            PITCH -> SportsSoccerPitch(page)
            TIMELINE -> SportsSoccerTimelinePanel(page)
            STATS -> SportsSoccerStats(page, SportsTeamStats.priorityRows(page))
            FORMATIONS -> SportsSoccerPitch(page, summaryOnly = true)
            INFO -> SportsMatchInfo(page)
        }
    }
}

/** Soccer's match-stat rules (Expo `SportsPriorityStats`, soccer variant). */
object SportsSoccerStatRules {
    /** Both shares as percentages adding up to 100 (±1), else null. */
    fun possession(left: String, right: String): Pair<Double, Double>? {
        fun number(text: String) = text.replace("%", "").trim().toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
        val a = number(left) ?: return null
        val b = number(right) ?: return null
        return if (abs(a + b - 100) <= 1) a to b else null
    }

    /** Accuracy comes as a fraction (0.83): shown as a percentage. */
    fun display(value: String, label: String): String {
        val number = value.trim().toDoubleOrNull()
        if (!SportsGameFormat.matches("accuracy %", label) || number == null || number !in 0.0..1.0) return value
        return "${(number * 100).roundToInt()}%"
    }

    fun format(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

/**
 * Soccer's match stats (Expo `SportsPriorityStats`, soccer variant; tvOS `SportsSoccerStats`): the
 * sides' crests, possession as one bar split between the team colours (when the two shares add up
 * to 100), then each statistic's figures around its name. One stop per row.
 */
@Composable
fun SportsSoccerStats(page: SportsGamePage, rows: List<SportsComparison>, title: String = "Match Stats") {
    val teams = page.teams
    val colors = teams.map { SportsTeamColors.color(it, page.game) }
    val possession = rows.firstOrNull { SportsGameFormat.matches("possession", it.label) }
    SportsPanel(title) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SportsTeamLogo(teams[0], 18.dp)
            Text(teams[0].shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(teams[1].shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            SportsTeamLogo(teams[1], 18.dp)
        }
        possession?.let { row ->
            val values = teams.map { page.value(row, it) }
            val shares = SportsSoccerStatRules.possession(values[0], values[1])
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shares?.let { "${SportsSoccerStatRules.format(it.first)}%" } ?: values[0], fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Possession", fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text(shares?.let { "${SportsSoccerStatRules.format(it.second)}%" } ?: values[1], fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                shares?.let { (a, b) ->
                    Row(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)) {
                        if (a > 0) Box(Modifier.weight(a.toFloat()).fillMaxHeight().background(colors[0]))
                        if (b > 0) Box(Modifier.weight(b.toFloat()).fillMaxHeight().background(colors[1]))
                    }
                }
            }
        }
        rows.filter { it != possession }.forEach { row ->
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    teams.forEachIndexed { index, team ->
                        if (index == 1) Text(row.label, fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(3f))
                        Text(
                            SportsSoccerStatRules.display(page.value(row, team), row.label), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, style = SportsDigits, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
