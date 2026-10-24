package com.strmr.tv.ui.sports.game.basketball

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.game.SportsAvailabilityNotice
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGameLeaders
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsKeyPlays
import com.strmr.tv.ui.sports.game.SportsMatchInfo
import com.strmr.tv.ui.sports.game.SportsPeriodBreakdown
import com.strmr.tv.ui.sports.game.SportsPregamePreview
import com.strmr.tv.ui.sports.game.availabilityApplies
import com.strmr.tv.ui.sports.game.charts.SportsLeadHistoryChart
import com.strmr.tv.ui.sports.game.charts.SportsLeadSeries
import com.strmr.tv.ui.sports.game.charts.SportsWinProbabilityChart
import com.strmr.tv.ui.sports.game.charts.SportsWinProbabilitySeries
import com.strmr.tv.ui.sports.game.hasMatchNotes
import com.strmr.tv.ui.sports.game.matchInfo

/**
 * The Overview of NBA, WNBA and college basketball games (Expo `SportsGameDetailTV`'s basketball
 * columns on TV, tvOS `SportsBasketballOverview`): the shot chart, each side's top scorer and the
 * lead history beside the key team statistics, the win probability, the game leaders (when the box
 * score has no points), the scoring by period, the recent or scoring plays and the game's facts.
 * A game not under way opens with the availability notice; a scheduled one ends with the matchup
 * preview. The two columns span the row, so Left and Right always reach the other one.
 */
@Composable
fun SportsBasketballOverview(page: SportsGamePage) {
    val left = listOf(Panel.AVAILABILITY, Panel.COURT, Panel.SCORERS, Panel.LEAD).filter { it.shows(page) }
    val right = listOf(Panel.STATS, Panel.WIN_PROBABILITY, Panel.LEADERS, Panel.PERIODS, Panel.PLAYS, Panel.INFO).filter { it.shows(page) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (left.isNotEmpty() || right.isNotEmpty()) {
            SportsColumns(fullHeight = true) {
                if (left.isNotEmpty()) SportsColumn(1f) { left.forEach { it.Content(page) } }
                if (right.isNotEmpty()) SportsColumn(1f) { right.forEach { it.Content(page) } }
            }
        }
        if (page.lifecycle == SportsLifecycle.SCHEDULED) SportsPregamePreview(page, page.detail.pregame)
    }
}

private enum class Panel {
    AVAILABILITY, COURT, SCORERS, LEAD, STATS, WIN_PROBABILITY, LEADERS, PERIODS, PLAYS, INFO;

    fun shows(page: SportsGamePage): Boolean {
        val detail = page.detail
        val started = page.lifecycle != SportsLifecycle.SCHEDULED
        return when (this) {
            AVAILABILITY -> page.availabilityApplies
            COURT -> sportsCourtShows(page)
            SCORERS -> started && scoringLeaders(page).isNotEmpty()
            LEAD -> SportsLeadSeries.of(page) != null
            STATS -> started && SportsBasketballStatRow.rows(detail.comparisons).isNotEmpty()
            WIN_PROBABILITY -> SportsWinProbabilitySeries.of(page) != null
            LEADERS -> detail.leaders.isNotEmpty() && !SCORERS.shows(page)
            PERIODS -> started && detail.periods.isNotEmpty()
            PLAYS -> detail.plays.isNotEmpty()
            INFO -> page.matchInfo.isNotEmpty() || page.hasMatchNotes
        }
    }

    @Composable
    fun Content(page: SportsGamePage) {
        when (this) {
            AVAILABILITY -> SportsAvailabilityNotice(page)
            COURT -> SportsBasketballCourtCard(page)
            SCORERS -> SportsBasketballScoringLeaders(page)
            LEAD -> SportsLeadSeries.of(page)?.let { SportsLeadHistoryChart(page, it) }
            STATS -> SportsBasketballTeamStats(page)
            WIN_PROBABILITY -> SportsWinProbabilitySeries.of(page)?.let { SportsWinProbabilityChart(page, it) }
            LEADERS -> SportsGameLeaders(page)
            PERIODS -> SportsPeriodBreakdown(page)
            PLAYS -> SportsKeyPlays(page)
            INFO -> SportsMatchInfo(page, "Game Information")
        }
    }
}
