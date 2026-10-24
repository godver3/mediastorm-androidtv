package com.strmr.tv.ui.sports.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsFormat
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.game.SportsPlayerPortrait
import com.strmr.tv.ui.sports.game.combat.SportsCombatRules

/**
 * A fight on the hub (Expo `CombatBody`, tvOS `SportsCombatCard`): both fighters' portraits and
 * names either side of "VS", the winner tagged, and the result (or the start). With scores hidden
 * the result says so. Emits the card's top and foot, which the card spreads over its height.
 */
@Composable
fun SportsCombatCard(game: SportsGame, leagueName: String, spoilers: Boolean, stale: Boolean) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SportsCardHeading(leagueName, game.lifecycle)
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Fighter(game, game.awayTeam, spoilers, Modifier.weight(1f))
            Text("VS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted, modifier = Modifier.padding(top = 16.dp))
            Fighter(game, game.homeTeam, spoilers, Modifier.weight(1f))
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val line = if (game.lifecycle == SportsLifecycle.SCHEDULED) SportsFormat.statusLine(game, spoilers) else SportsCombatRules.result(game, spoilers)
        Text(line, fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (stale) SportsCardDelayed()
    }
}

@Composable
private fun Fighter(game: SportsGame, athlete: SportsTeam, spoilers: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SportsPlayerPortrait(athlete.name, SportsCombatRules.portrait(game.league, athlete), athlete, 42.dp)
        Text(
            athlete.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!spoilers && game.lifecycle == SportsLifecycle.FINAL && athlete.winner == true) {
            Text("WINNER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SportsCombatRules.winner)
        }
    }
}
