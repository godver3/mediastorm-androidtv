package com.strmr.tv.ui.sports.game.rugby

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
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsMatchInfo
import com.strmr.tv.ui.sports.game.SportsPregamePreview
import com.strmr.tv.ui.sports.game.SportsTeamComparison
import com.strmr.tv.ui.sports.game.SportsTeamStats
import com.strmr.tv.ui.sports.game.availabilityApplies
import com.strmr.tv.ui.sports.game.hasMatchNotes
import com.strmr.tv.ui.sports.game.matchInfo

/**
 * The Overview of rugby union and rugby league matches (Expo `SportsSportOverview`'s rugby branch,
 * tvOS `SportsRugbyOverview`): the scoring and discipline timeline and the match comparison beside
 * the substitutions, the lineups and the match information. When the feed has no scoring events
 * the timeline (saying so) moves to the second column. A match not under way opens with the
 * availability notice; a scheduled one ends with the matchup preview.
 */
@Composable
fun SportsRugbyOverview(page: SportsGamePage) {
    val keyEvents = page.detail.plays.any(SportsRugby::isKeyEvent)
    val left = (listOf(Panel.AVAILABILITY) + (if (keyEvents) listOf(Panel.TIMELINE) else emptyList()) + Panel.STATS).filter { it.shows(page) }
    val right = ((if (keyEvents) emptyList() else listOf(Panel.TIMELINE)) + listOf(Panel.SUBSTITUTIONS, Panel.LINEUPS, Panel.INFO))
        .filter { it.shows(page) }
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

private enum class Panel {
    AVAILABILITY, TIMELINE, STATS, SUBSTITUTIONS, LINEUPS, INFO;

    fun shows(page: SportsGamePage): Boolean = when (this) {
        AVAILABILITY -> page.availabilityApplies
        TIMELINE -> SportsRugbyEvents.shows(page, substitutions = false)
        STATS -> page.lifecycle != SportsLifecycle.SCHEDULED && page.detail.comparisons.isNotEmpty()
        SUBSTITUTIONS -> SportsRugbyEvents.shows(page, substitutions = true)
        LINEUPS -> sportsRugbyLineupsShow(page)
        INFO -> page.matchInfo.isNotEmpty() || page.hasMatchNotes
    }

    @Composable
    fun Content(page: SportsGamePage) {
        when (this) {
            AVAILABILITY -> SportsAvailabilityNotice(page)
            TIMELINE -> SportsRugbyTimeline(page)
            STATS -> SportsTeamComparison(page, SportsTeamStats.priorityRows(page), SportsTeamStats.title(page.kind))
            SUBSTITUTIONS -> SportsRugbyTimeline(page, substitutions = true)
            LINEUPS -> SportsRugbyLineups(page)
            INFO -> SportsMatchInfo(page)
        }
    }
}
