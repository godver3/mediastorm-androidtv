package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.tv.sports.SportsLifecycle

/**
 * The generic panels an Overview is built from (Expo's shared detail panels), each shown only
 * when the feed sent its data. A sport's own Overview can reuse them with its panels
 * ([SportsOverviewLayout]).
 */
enum class SportsOverviewPanel {
    /** What the feed hasn't sent, or that play is held up (Expo `SportsMatchAvailability`). */
    AVAILABILITY,

    /** Points per period (Expo `SportsPeriodBreakdown`). */
    PERIODS,

    /** The sport's key team statistics side by side (Expo `SportsPriorityStats` / `StatsPanel`). */
    TEAM_STATS,

    /** The latest or scoring plays (Expo `RecentDetailPlays`). */
    KEY_PLAYS,

    /** The feed's game leaders (Expo `CompactDetailLeaders`). */
    LEADERS,

    /** Baseball's top batter and pitcher, hockey's goalies (Expo `SportsResultPerformers`). */
    PERFORMERS,

    /** Venue, broadcast, format, records and coverage (Expo `EventInfo`, `SportsTeamContext`). */
    MATCH_INFO;

    fun shows(page: SportsGamePage): Boolean {
        val detail = page.detail
        val started = page.lifecycle != SportsLifecycle.SCHEDULED
        return when (this) {
            AVAILABILITY -> page.availabilityApplies
            PERIODS -> started && detail.periods.isNotEmpty()
            TEAM_STATS -> started && detail.comparisons.isNotEmpty()
            KEY_PLAYS -> detail.plays.isNotEmpty()
            LEADERS -> detail.leaders.isNotEmpty()
            PERFORMERS -> page.performers.isNotEmpty()
            MATCH_INFO -> page.matchInfo.isNotEmpty() || page.hasMatchNotes
        }
    }

    @Composable
    fun Content(page: SportsGamePage) {
        when (this) {
            AVAILABILITY -> SportsAvailabilityNotice(page)
            PERIODS -> SportsPeriodBreakdown(page)
            TEAM_STATS -> SportsTeamComparison(page, SportsTeamStats.priorityRows(page), SportsTeamStats.title(page.kind))
            KEY_PLAYS -> SportsKeyPlays(page)
            LEADERS -> SportsGameLeaders(page)
            PERFORMERS -> SportsResultPerformers(page)
            MATCH_INFO -> SportsMatchInfo(page)
        }
    }
}

/**
 * An Overview in two columns (Expo `SportsOverviewLayout`: [primary] 1.2, [secondary] 1; one
 * column when the other has nothing), then the matchup preview before the start. Left and Right
 * move between the columns.
 */
@Composable
fun SportsOverviewLayout(page: SportsGamePage, primary: List<SportsOverviewPanel>, secondary: List<SportsOverviewPanel>) {
    val left = primary.filter { it.shows(page) }
    val right = secondary.filter { it.shows(page) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (left.isNotEmpty() || right.isNotEmpty()) {
            SportsColumns {
                if (left.isNotEmpty()) SportsColumn(1.2f) { left.forEach { it.Content(page) } }
                if (right.isNotEmpty()) SportsColumn(1f) { right.forEach { it.Content(page) } }
            }
        }
        if (page.lifecycle == SportsLifecycle.SCHEDULED) SportsPregamePreview(page, page.detail.pregame)
    }
}

/**
 * The Overview of every sport without its own yet (Expo `SportsSportOverview`'s default and the
 * shared panels of `SportsGameDetailTV`): availability, line score, team stats and key plays
 * beside the leaders, standout performers and match information.
 */
@Composable
fun SportsGenericOverview(page: SportsGamePage) {
    SportsOverviewLayout(page, GenericPrimary, GenericSecondary)
}

private val GenericPrimary = listOf(
    SportsOverviewPanel.AVAILABILITY, SportsOverviewPanel.PERIODS, SportsOverviewPanel.TEAM_STATS, SportsOverviewPanel.KEY_PLAYS,
)
private val GenericSecondary = listOf(SportsOverviewPanel.LEADERS, SportsOverviewPanel.PERFORMERS, SportsOverviewPanel.MATCH_INFO)
