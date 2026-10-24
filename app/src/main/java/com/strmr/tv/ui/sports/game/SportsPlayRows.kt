package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsPlay
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.hockey.SportsHockeyGoalBadge
import com.strmr.tv.ui.sports.game.hockey.SportsHockeyGoalBadgeTag

/**
 * A play: when (period and clock), what happened and the narrative. Scoring plays stand out with
 * a bar and a tint in the scoring side's colour (Expo's play rows mark baseball's scoring plays
 * the same way). [compact] is the Overview's form: the time over one line of narrative, after the
 * side's logo with [teamMark] (Expo `FootballRecentPlays`).
 */
@Composable
fun SportsPlayRow(page: SportsGamePage, play: SportsPlay, modifier: Modifier = Modifier, compact: Boolean = false, teamMark: Boolean = false) {
    val color = if (play.scoring) page.team(play.teamId)?.let { SportsTeamColors.color(it, page.game) } ?: Accent else Color.Transparent
    SportsReadout(modifier, tint = if (play.scoring) color.copy(alpha = 0.14f) else null) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(color))
            if (compact) {
                if (teamMark) page.team(play.teamId)?.let { SportsTeamLogo(it, 20.dp) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(time(play, " · "), fontSize = 11.sp, color = SportsMuted)
                    Text(
                        (play.description ?: play.title).trim(), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (play.scoring) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            } else {
                Text(time(play, "\n"), fontSize = 11.sp, color = SportsMuted, modifier = Modifier.width(60.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(play.title, fontSize = 14.sp, fontWeight = if (play.scoring) FontWeight.Bold else FontWeight.SemiBold)
                        if (play.scoring) Text("Scoring Play", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SportsTeamColors.scoring)
                    }
                    if (page.game.league == "nhl") SportsHockeyGoalBadge.of(play)?.let { SportsHockeyGoalBadgeTag(it) }
                    play.description?.trim()?.takeIf { it != play.title }?.let {
                        Text(it, fontSize = 13.sp, color = SportsMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun time(play: SportsPlay, separator: String) =
    listOfNotNull(play.periodLabel, play.clock).filter { it.isNotEmpty() }.joinToString(separator)

/**
 * The Overview's key plays (Expo `RecentDetailPlays`): a final's scoring plays, else the newest
 * plays (baseball without its pitch-by-pitch lines); three at most, newest first. Football's
 * show each side's logo ([teamMarks]).
 */
@Composable
fun SportsKeyPlays(page: SportsGamePage, teamMarks: Boolean = false) {
    val (title, plays) = page.keyPlays
    SportsPanel(title) {
        plays.forEach { SportsPlayRow(page, it, compact = true, teamMark = teamMarks) }
    }
}

/** The key plays' title and plays ([SportsKeyPlays]). */
val SportsGamePage.keyPlays: Pair<String, List<SportsPlay>>
    get() {
        val plays = detail.plays
        val final = lifecycle == SportsLifecycle.FINAL
        val picked = when {
            final -> plays.filter { it.scoring }
            game.league == "mlb" -> plays.filterNot { SportsGameFormat.matches("""^pitch\s*\d*\s*:""", it.title) }
            else -> plays
        }
        val title = if (final && picked.isNotEmpty()) "Scoring Plays" else "Recent Plays"
        return title to picked.ifEmpty { plays.take(1) }.take(3)
    }
