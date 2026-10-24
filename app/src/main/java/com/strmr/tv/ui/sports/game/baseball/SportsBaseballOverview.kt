package com.strmr.tv.ui.sports.game.baseball

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsAvailabilityNotice
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGameLeaders
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsKeyPlays
import com.strmr.tv.ui.sports.game.SportsPregamePreview
import com.strmr.tv.ui.sports.game.SportsResultPerformers
import com.strmr.tv.ui.sports.game.SportsTeamComparison
import com.strmr.tv.ui.sports.game.SportsTeamStats
import com.strmr.tv.ui.sports.game.availabilityApplies
import com.strmr.tv.ui.sports.game.performers

/**
 * The Overview of MLB games (Expo `SportsBaseballOverview`, tvOS `SportsBaseballOverview`): while
 * live, the at-bat (or why there is none); the line score and a final's standout batter and pitcher
 * per side; beside them the recent or scoring plays, the game leaders, the team comparison, and
 * while an at-bat is under way a note that pitch locations aren't drawn. A game not under way
 * opens with the availability notice; a scheduled one ends with the matchup preview.
 */
@Composable
fun SportsBaseballOverview(page: SportsGamePage) {
    val state = SportsBaseballState.of(page.game)
    val left = listOf(Panel.AVAILABILITY, Panel.AT_BAT, Panel.LINE_SCORE, Panel.PERFORMERS).filter { it.shows(page, state) }
    val right = listOf(Panel.PLAYS, Panel.LEADERS, Panel.COMPARISON, Panel.PITCHES).filter { it.shows(page, state) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (left.isNotEmpty() || right.isNotEmpty()) {
            SportsColumns {
                if (left.isNotEmpty()) SportsColumn(1.1f) { left.forEach { it.Content(page, state) } }
                if (right.isNotEmpty()) SportsColumn(1f) { right.forEach { it.Content(page, state) } }
            }
        }
        if (page.lifecycle == SportsLifecycle.SCHEDULED) SportsPregamePreview(page, page.detail.pregame)
    }
}

private enum class Panel {
    AVAILABILITY, AT_BAT, LINE_SCORE, PERFORMERS, PLAYS, LEADERS, COMPARISON, PITCHES;

    fun shows(page: SportsGamePage, state: SportsBaseballState): Boolean {
        val detail = page.detail
        val started = page.lifecycle != SportsLifecycle.SCHEDULED
        return when (this) {
            AVAILABILITY -> page.availabilityApplies
            AT_BAT -> page.lifecycle == SportsLifecycle.LIVE
            LINE_SCORE -> started && detail.periods.isNotEmpty()
            PERFORMERS -> page.performers.isNotEmpty()
            PLAYS -> detail.plays.isNotEmpty()
            LEADERS -> detail.leaders.isNotEmpty()
            COMPARISON -> started && detail.comparisons.isNotEmpty()
            PITCHES -> state.active
        }
    }

    @Composable
    fun Content(page: SportsGamePage, state: SportsBaseballState) {
        when (this) {
            AVAILABILITY -> SportsAvailabilityNotice(page)
            AT_BAT -> SportsBaseballAtBat(page, state)
            LINE_SCORE -> SportsBaseballLineScoreView(page)
            PERFORMERS -> SportsResultPerformers(page)
            PLAYS -> SportsKeyPlays(page)
            LEADERS -> SportsGameLeaders(page)
            COMPARISON -> SportsTeamComparison(page, SportsTeamStats.priorityRows(page), "Team Comparison")
            PITCHES -> SportsReadout {
                Text(
                    if (page.detail.capabilities.plays) "Pitch locations are not supplied in this view. Reported pitches appear in Play-by-Play."
                    else "Pitch locations are not supplied in this view.",
                    fontSize = 12.sp, color = SportsMuted,
                )
            }
        }
    }
}

/**
 * MLB's line score (Expo `SportsPeriodBreakdown` for MLB, "Line score"): each side's runs by
 * inning, at least nine, the inning being played in the accent colour, then runs, hits and errors.
 * One stop for the remote; columns narrow to fit extra innings.
 */
@Composable
fun SportsBaseballLineScoreView(page: SportsGamePage) {
    val score = SportsBaseballLineScore.of(page)
    val spoken = page.teams.joinToString(". ") { team ->
        val home = team.id == page.game.homeTeam.id
        "${team.name}: ${score.innings.joinToString(", ") { if (home) it.home else it.away }}. " +
            score.totals.joinToString(", ") { "${it.label} ${if (home) it.home else it.away}" }
    }
    SportsPanel("Line Score") {
        SportsReadout(Modifier.semantics { contentDescription = spoken }) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cell = Cells.firstOrNull { TeamWidth + it * score.innings.size + (it + 4.dp) * 3 + 9.dp <= maxWidth } ?: Cells.last()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell("TEAM", TeamWidth, SportsMuted, 11, align = TextAlign.Start)
                        score.innings.forEach { Cell("${it.number}", cell, if (it.number == score.current) Accent else SportsMuted, 11) }
                        Divider()
                        score.totals.forEach { Cell(it.label, cell + 4.dp, SportsMuted, 11) }
                    }
                    page.teams.forEach { team ->
                        val home = team.id == page.game.homeTeam.id
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(TeamWidth), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SportsTeamLogo(team, 18.dp)
                                Text(team.shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            score.innings.forEach { Cell(if (home) it.home else it.away, cell) }
                            Divider()
                            score.totals.forEach { Cell(if (home) it.home else it.away, cell + 4.dp, if (it.label == "R") Accent else Color.White, bold = true) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(14.dp).background(Color.White.copy(alpha = 0.2f)))
}

@Composable
private fun Cell(text: String, width: Dp, color: Color = Color.White, size: Int = 14, bold: Boolean = false, align: TextAlign = TextAlign.Center) {
    Text(
        text, fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align,
        maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.width(width),
    )
}

private val TeamWidth = 62.dp
private val Cells = listOf(26.dp, 21.dp, 17.dp)
