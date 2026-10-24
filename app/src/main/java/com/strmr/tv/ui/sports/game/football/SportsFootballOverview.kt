package com.strmr.tv.ui.sports.game.football

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.game.SportsAvailabilityNotice
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsComparisonStyle
import com.strmr.tv.ui.sports.game.SportsGameLeaders
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsKeyPlays
import com.strmr.tv.ui.sports.game.SportsMatchInfo
import com.strmr.tv.ui.sports.game.SportsPeriodBreakdown
import com.strmr.tv.ui.sports.game.SportsPregamePreview
import com.strmr.tv.ui.sports.game.SportsTeamComparison
import com.strmr.tv.ui.sports.game.availabilityApplies
import com.strmr.tv.ui.sports.game.hasMatchNotes
import com.strmr.tv.ui.sports.game.matchInfo

/**
 * The Overview of NFL and college football games (Expo `FootballOverview`, tvOS
 * `SportsFootballOverview`): the possession field and the recent or scoring plays beside the team
 * comparison and the scoring by quarter, then the latest drives beside the game leaders and the
 * game's facts. A game that isn't under way opens with the availability notice; a scheduled one
 * carries the matchup preview before the facts. The field keeps a column's width: with nothing
 * beside it, it takes the right-hand column. Left and Right move between the columns.
 */
@Composable
fun SportsFootballOverview(page: SportsGamePage) {
    val possession = SportsFootballPossession.of(page)
    val right = listOf(Panel.COMPARISON, Panel.QUARTERS).filter { it.shows(page) }
    val field = if (possession == null) emptyList() else listOf(Panel.FIELD)
    val left = listOf(Panel.AVAILABILITY).filter { it.shows(page) } + (if (right.isEmpty()) emptyList() else field) +
        listOf(Panel.PLAYS).filter { it.shows(page) }
    val lower = listOf(listOf(Panel.DRIVES), listOf(Panel.LEADERS, Panel.INFO)).map { column -> column.filter { it.shows(page) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val top = right.ifEmpty { field }
        if (left.isNotEmpty() || top.isNotEmpty()) {
            SportsColumns {
                if (left.isNotEmpty()) SportsColumn(1.1f) { left.forEach { it.Content(page, possession) } }
                if (top.isNotEmpty()) SportsColumn(1f) { top.forEach { it.Content(page, possession) } }
            }
        }
        if (page.lifecycle == SportsLifecycle.SCHEDULED) {
            SportsPregamePreview(page, page.detail.pregame)
        } else if (lower.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = SportsMuted, modifier = Modifier.size(16.dp))
                Text("More game details below", fontSize = 12.sp, color = SportsMuted)
            }
        }
        if (lower.any { it.isNotEmpty() }) {
            SportsColumns {
                lower.forEachIndexed { index, panels ->
                    if (panels.isNotEmpty()) SportsColumn(if (index == 0) 1.5f else 1f) { panels.forEach { it.Content(page, possession) } }
                }
            }
        }
    }
}

private enum class Panel {
    AVAILABILITY, FIELD, PLAYS, COMPARISON, QUARTERS, DRIVES, LEADERS, INFO;

    fun shows(page: SportsGamePage): Boolean {
        val detail = page.detail
        return when (this) {
            AVAILABILITY -> page.availabilityApplies
            FIELD -> page.isFootballLeague
            PLAYS -> detail.plays.isNotEmpty()
            COMPARISON -> detail.comparisons.isNotEmpty()
            QUARTERS -> detail.periods.isNotEmpty()
            DRIVES -> page.showsRecentDrives
            LEADERS -> detail.leaders.isNotEmpty()
            INFO -> page.matchInfo.isNotEmpty() || page.hasMatchNotes
        }
    }

    @Composable
    fun Content(page: SportsGamePage, possession: SportsFootballPossession?) {
        when (this) {
            AVAILABILITY -> SportsAvailabilityNotice(page)
            FIELD -> possession?.let { SportsFootballPossessionField(page, it) }
            PLAYS -> SportsKeyPlays(page, teamMarks = true)
            COMPARISON -> SportsTeamComparison(page, page.detail.comparisons.take(5), "Team Comparison", SportsComparisonStyle.SHARE)
            QUARTERS -> SportsPeriodBreakdown(page)
            DRIVES -> SportsRecentDrives(page, possession)
            LEADERS -> SportsGameLeaders(page)
            INFO -> SportsMatchInfo(page, "Game Information")
        }
    }
}
