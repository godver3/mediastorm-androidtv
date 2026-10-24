package com.strmr.tv.ui.sports.game.tennis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsAccent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsAvailabilityNotice
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsPregamePreview
import com.strmr.tv.ui.sports.game.SportsTeamComparison
import com.strmr.tv.ui.sports.game.SportsTeamStats
import com.strmr.tv.ui.sports.game.availabilityApplies

/**
 * The Overview of ATP and WTA matches (Expo `SportsSportOverview`'s tennis branch, tvOS
 * `SportsTennisOverview`): the set scores and the latest match action beside the serve and return
 * statistics and the match's draw, court, format, venue and channels. A match not under way opens
 * with the availability notice; a scheduled one ends with the matchup preview.
 */
@Composable
fun SportsTennisOverview(page: SportsGamePage) {
    val left = listOf(Panel.AVAILABILITY, Panel.SETS, Panel.ACTION).filter { it.shows(page) }
    val right = listOf(Panel.STATS, Panel.INFO).filter { it.shows(page) }
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

/**
 * "US Open · Men's Singles · 1st Round · Arthur Ashe Stadium · Best of 5 sets", then where and on
 * which channels (Expo's tennis `EventInfo`).
 */
private fun info(page: SportsGamePage): List<String> {
    val game = page.game
    val format = game.bestOf?.takeIf { it == 3 || it == 5 }?.let { "Best of $it sets" }
    return listOf(
        listOf(SportsTennis.eventContext(game), game.courtName, format),
        listOf(game.venue, game.broadcasts?.joinToString(" · ")),
    ).map { line -> line.filterNot { it.isNullOrEmpty() }.joinToString(" · ") }.filter { it.isNotEmpty() }
}

private enum class Panel {
    AVAILABILITY, SETS, ACTION, STATS, INFO;

    fun shows(page: SportsGamePage): Boolean {
        val started = page.lifecycle != SportsLifecycle.SCHEDULED
        return when (this) {
            AVAILABILITY -> page.availabilityApplies
            SETS -> started && page.detail.periods.isNotEmpty()
            ACTION -> page.detail.plays.isNotEmpty()
            STATS -> started && page.detail.comparisons.isNotEmpty()
            INFO -> info(page).isNotEmpty() || page.coverageNote != null
        }
    }

    @Composable
    fun Content(page: SportsGamePage) {
        when (this) {
            AVAILABILITY -> SportsAvailabilityNotice(page)
            SETS -> SportsTennisSetScores(page)
            ACTION -> SportsKeyEvents(page, "Match Action")
            STATS -> SportsTeamComparison(page, SportsTeamStats.priorityRows(page), SportsTeamStats.title(page.kind))
            INFO -> SportsPanel("Match Information") {
                val lines = info(page)
                if (lines.isNotEmpty()) SportsReadout { lines.forEach { Text(it, fontSize = 14.sp) } }
                page.coverageNote?.let { note -> SportsReadout { Text(note, fontSize = 12.sp, color = SportsMuted) } }
            }
        }
    }
}

/**
 * The latest events of a match (Expo `KeyEvents`, tvOS `SportsKeyEvents`): up to four, newest
 * first, each with when it happened, its title and the narrative; scoring ones marked in the
 * accent. One stop each.
 */
@Composable
fun SportsKeyEvents(page: SportsGamePage, title: String) {
    SportsPanel(title) {
        page.detail.plays.take(4).forEach { play ->
            SportsReadout {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier.width(2.dp).fillMaxHeight().clip(CircleShape)
                            .background(if (play.scoring) SportsAccent else Color.White.copy(alpha = 0.25f)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val time = listOfNotNull(play.periodLabel, play.clock).filter { it.isNotEmpty() }
                        if (time.isNotEmpty()) Text(time.joinToString(" · "), fontSize = 12.sp, color = SportsMuted)
                        Text(play.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        play.description?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
                    }
                }
            }
        }
    }
}
