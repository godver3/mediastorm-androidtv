package com.strmr.tv.ui.sports.game.soccer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.ui.sports.SportsAccent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsEventKind
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors

/**
 * The Lineups tab of soccer matches (Expo `SportsGameDetailTV`'s lineups tab, tvOS
 * `SportsLineupsPanel`): the published formations on the pitch over both sides' players, beside
 * the match's goals and its first three statistics when the feed has events (columns 2 : 1).
 * Left and Right move between the columns.
 */
@Composable
fun SportsLineupsPanel(page: SportsGamePage) {
    val side = page.detail.plays.isNotEmpty()
    SportsColumns(fullHeight = true) {
        SportsColumn(2f) {
            SportsSoccerPitch(page)
            SportsLineupLists(page)
        }
        if (side) {
            SportsColumn(1f) {
                SportsSoccerGoals(page)
                if (page.detail.comparisons.isNotEmpty()) SportsSoccerStats(page, page.detail.comparisons.take(3))
            }
        }
    }
}

/**
 * The match's goals, newest first (Expo `PlaysPanel` in its highlights mode, tvOS
 * `SportsSoccerGoals`): minute, side, scorer and narrative, marked in the scoring side's colour.
 * One stop per goal.
 */
@Composable
fun SportsSoccerGoals(page: SportsGamePage) {
    val goals = page.detail.plays.filter { SportsEventKind.of(it.title) == SportsEventKind.GOAL || it.scoring }
    SportsPanel("Goals", note = if (goals.isEmpty()) null else "Newest first · Every key event in Commentary") {
        if (goals.isEmpty()) SportsReadout { Text("No goals in the loaded feed.", fontSize = 14.sp, color = SportsMuted) }
        goals.forEach { play ->
            val team = page.team(play.teamId)
            SportsReadout {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SportsSoccerEventIcon(play, team?.let { SportsTeamColors.color(it, page.game) } ?: SportsMuted, 18.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            listOfNotNull(play.clock, team?.shortName, if (play.shootout) "Shootout" else null).joinToString(" · "),
                            fontSize = 12.sp, color = SportsAccent, style = SportsDigits,
                        )
                        Text(play.participants.firstOrNull()?.name ?: play.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        play.description?.let { Text(it, fontSize = 12.sp, color = SportsMuted, maxLines = 3) }
                    }
                }
            }
        }
    }
}
