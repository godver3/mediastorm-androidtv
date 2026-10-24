package com.strmr.tv.ui.sports.game.football

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsPlay
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors

/**
 * A play on NFL and college football's Play-by-Play tab (Expo `FootballPlays`, tvOS
 * `SportsFootballPlayRow`): the quarter over the clock, a timeline marker (a gold diamond for a
 * score), the side's logo, then the play's title, the narrative and a gold "Scoring Play" tag.
 */
@Composable
fun SportsFootballPlayRow(page: SportsGamePage, play: SportsPlay, modifier: Modifier = Modifier) {
    SportsReadout(modifier) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(48.dp)) {
                Text(play.periodLabel, fontSize = 11.sp, color = SportsMuted)
                Text(play.clock.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            // The timeline runs down every row.
            Box(Modifier.width(13.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.18f)))
                if (play.scoring) {
                    Box(Modifier.size(8.dp).rotate(45f).background(SportsTeamColors.scoring))
                } else {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(SportsMuted))
                }
            }
            Box(Modifier.size(24.dp)) { page.team(play.teamId)?.let { SportsTeamLogo(it, 24.dp) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(play.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                play.description?.trim()?.takeIf { it != play.title }?.let {
                    Text(it, fontSize = 13.sp, color = SportsMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                if (play.scoring) Text("Scoring Play", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SportsTeamColors.scoring)
            }
        }
    }
}
