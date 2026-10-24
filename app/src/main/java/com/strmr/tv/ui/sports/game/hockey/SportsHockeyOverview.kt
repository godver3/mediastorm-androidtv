package com.strmr.tv.ui.sports.game.hockey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.liveHockeySituation
import com.strmr.tv.ui.sports.game.SportsAvailabilityNotice
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsMatchInfo
import com.strmr.tv.ui.sports.game.SportsPeriodBreakdown
import com.strmr.tv.ui.sports.game.SportsPregamePreview
import com.strmr.tv.ui.sports.game.SportsResultPerformers
import com.strmr.tv.ui.sports.game.SportsTeamComparison
import com.strmr.tv.ui.sports.game.SportsTeamStats
import com.strmr.tv.ui.sports.game.availabilityApplies
import com.strmr.tv.ui.sports.game.hasMatchNotes
import com.strmr.tv.ui.sports.game.matchInfo
import com.strmr.tv.ui.sports.game.performers

/**
 * The Overview of NHL games (Expo `SportsSportOverview`'s ice-hockey branch, tvOS
 * `SportsHockeyOverview`): the latest event or a final's scoring highlights and the match stats
 * (with the reported strength while live) beside the goals by period, the shot-attempt map, the
 * goalies and the game's facts. A game not under way opens with the availability notice; a
 * scheduled one ends with the matchup preview. The columns (1.2 : 1) span the row.
 */
@Composable
fun SportsHockeyOverview(page: SportsGamePage) {
    val left = listOf(Panel.AVAILABILITY, Panel.TIMELINE, Panel.STATS).filter { it.shows(page) }
    val right = listOf(Panel.PERIODS, Panel.SHOTS, Panel.GOALIES, Panel.INFO).filter { it.shows(page) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (left.isNotEmpty() || right.isNotEmpty()) {
            SportsColumns(fullHeight = true) {
                if (left.isNotEmpty()) SportsColumn(1.2f) { left.forEach { it.Content(page) } }
                if (right.isNotEmpty()) SportsColumn(1f) { right.forEach { it.Content(page) } }
            }
        }
        if (page.lifecycle == SportsLifecycle.SCHEDULED) SportsPregamePreview(page, page.detail.pregame)
    }
}

/** "Reported strength · Boston Bruins power play · 1:18" while live ("Last reported…" when old). */
private fun strength(page: SportsGamePage): String? {
    val strength = page.game.liveHockeySituation?.strength?.ifEmpty { null } ?: return null
    return if (page.isStale()) "Last reported strength · $strength" else "Reported strength · $strength"
}

private enum class Panel {
    AVAILABILITY, TIMELINE, STATS, PERIODS, SHOTS, GOALIES, INFO;

    fun shows(page: SportsGamePage): Boolean {
        val started = page.lifecycle != SportsLifecycle.SCHEDULED
        return when (this) {
            AVAILABILITY -> page.availabilityApplies
            TIMELINE -> hockeyTimelineEvents(page).isNotEmpty()
            STATS -> started && (page.detail.comparisons.isNotEmpty() || strength(page) != null)
            PERIODS -> started && page.detail.periods.isNotEmpty()
            SHOTS -> hockeyRinkShots(page).isNotEmpty()
            GOALIES -> page.performers.isNotEmpty()
            INFO -> page.matchInfo.isNotEmpty() || page.hasMatchNotes
        }
    }

    @Composable
    fun Content(page: SportsGamePage) {
        when (this) {
            AVAILABILITY -> SportsAvailabilityNotice(page)
            TIMELINE -> SportsHockeyTimeline(page)
            STATS -> SportsTeamComparison(page, SportsTeamStats.priorityRows(page), SportsTeamStats.title(page.kind), note = strength(page))
            PERIODS -> SportsPeriodBreakdown(page)
            SHOTS -> SportsHockeyShotMap(page)
            GOALIES -> SportsResultPerformers(page)
            INFO -> SportsMatchInfo(page)
        }
    }
}
