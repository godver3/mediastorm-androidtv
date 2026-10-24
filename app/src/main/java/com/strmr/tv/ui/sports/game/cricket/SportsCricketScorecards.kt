package com.strmr.tv.ui.sports.game.cricket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsCricketPlayer
import com.strmr.tv.sports.SportsCricketScorecard
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsFigure
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsPlayerPortrait

/**
 * The player scorecards (Expo `SportsCricketScorecards`, tvOS `SportsCricketScorecards`): a final's
 * top batters and bowlers of the last innings ("Reported Innings 2 Highlights"), then each batting
 * and bowling card with every player's figures and dismissal and the extras. One stop per player.
 */
@Composable
fun SportsCricketScorecards(page: SportsGamePage) {
    val cards = if (page.lifecycle == SportsLifecycle.SCHEDULED) emptyList() else page.detail.cricketScorecards
    val (innings, highlights) = SportsCricket.highlights(page)
    if (highlights.isNotEmpty()) {
        SportsPanel("Reported Innings $innings Highlights") {
            highlights.forEach { entry ->
                SportsReadout {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SportsPlayerPortrait(entry.player.name, entry.player.headshotUrl, null, 24.dp)
                        Text(entry.player.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("${entry.value} ${entry.metric}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (cards.isNotEmpty()) {
        Text("Reported player scorecards · Available innings only", fontSize = 12.sp, color = SportsMuted, modifier = Modifier.padding(horizontal = 4.dp))
    }
    cards.forEach { card ->
        val side = if (card.kind == SportsCricketScorecard.Kind.BATTING) "Batting" else "Bowling"
        val note = listOfNotNull("Match innings ${card.innings}", if (page.isStale()) "Last reported" else null).joinToString(" · ")
        SportsPanel("${card.teamName} · $side", note = note) {
            card.players.forEach { Player(it) }
            card.extras?.let { Text("Extras $it", fontSize = 12.sp, color = SportsMuted, modifier = Modifier.padding(horizontal = 10.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Player(player: SportsCricketPlayer) {
    SportsReadout {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SportsPlayerPortrait(player.name, player.headshotUrl, null, 20.dp)
            Text(player.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            player.stats.forEach { SportsFigure(it.label, it.value) }
        }
        player.dismissal?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
    }
}
