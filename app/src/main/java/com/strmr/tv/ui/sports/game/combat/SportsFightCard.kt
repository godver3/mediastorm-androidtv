package com.strmr.tv.ui.sports.game.combat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsCombatBout
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.sports.SportsAccent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsPlayerPortrait

/**
 * The whole fight card in the feed's order (Expo "Fight card" with `SportsCombatBoutRow`, tvOS
 * `SportsFightCard`): each bout's fighters, division and result; the bout on screen says
 * "Viewing", every other one opens its own page on Select. Results are left out with scores
 * [hidden].
 */
@Composable
fun SportsFightCard(page: SportsGamePage, hidden: Boolean) {
    val bouts = page.game.combat?.bouts.orEmpty()
    val navigator = LocalNavigator.current
    SportsPanel("Fight Card", note = if (bouts.isEmpty()) null else "Provider order · Main card and prelim sections are not supplied.") {
        if (bouts.isEmpty()) {
            SportsReadout { Text("The provider has not supplied the full fight card.", fontSize = 14.sp, color = SportsMuted) }
        }
        bouts.forEach { bout ->
            if (bout.id == page.game.id) {
                SportsReadout(Modifier.border(1.5.dp, SportsAccent.copy(alpha = 0.7f), RoundedCornerShape(8.dp))) { Bout(bout, hidden, viewing = true) }
            } else {
                SportsReadout(onClick = { navigator.open(Page.SportsGame(bout.id)) }) { Bout(bout, hidden, viewing = false) }
            }
        }
    }
}

@Composable
private fun Bout(bout: SportsCombatBout, hidden: Boolean, viewing: Boolean) {
    val line = listOfNotNull(bout.division, SportsCombatRules.result(bout, hidden)).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${bout.away.name} vs ${bout.home.name}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (viewing) Text("Viewing", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SportsAccent)
    }
    if (line.isNotEmpty()) Text(line, fontSize = 12.sp, color = SportsMuted)
}

/**
 * The scoreboard of a bout or fight card (Expo `SportsCombatHeader`, tvOS `SportsCombatHeader`):
 * both fighters' portraits and names either side of "VS" with the result, or the card's title
 * when the fighters aren't named; before the fight the division and start. With scores hidden
 * the result says so.
 */
@Composable
fun SportsCombatHeader(page: SportsGamePage, spoilers: Boolean) {
    val game = page.game
    val named = SportsCombatRules.named(game)
    val line = when {
        page.lifecycle == SportsLifecycle.SCHEDULED -> listOfNotNull(game.combat?.division, SportsGameFormat.detailStatus(page)).joinToString(" · ")
        spoilers -> SportsCombatRules.result(game, hidden = true)
        named -> SportsCombatRules.result(game, hidden = false)
        else -> game.statusDetail?.ifEmpty { null } ?: "Result not supplied"
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (named) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HeaderFighter(page, game.awayTeam, trailing = false, spoilers, Modifier.weight(1f))
                Text("VS", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted)
                HeaderFighter(page, game.homeTeam, trailing = true, spoilers, Modifier.weight(1f))
            }
        } else {
            Text(game.displayTitle, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
        Text(line, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** A fighter of the header: the portrait towards the centre, the name (and WINNER) outside it. */
@Composable
private fun HeaderFighter(page: SportsGamePage, athlete: SportsTeam, trailing: Boolean, spoilers: Boolean, modifier: Modifier) {
    val won = !spoilers && page.lifecycle == SportsLifecycle.FINAL && athlete.winner == true
    Row(
        modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, if (trailing) Alignment.Start else Alignment.End),
    ) {
        if (trailing) SportsPlayerPortrait(athlete.name, SportsCombatRules.portrait(page.game.league, athlete), athlete, 60.dp)
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (trailing) Alignment.Start else Alignment.End) {
            Text(
                athlete.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (trailing) TextAlign.Start else TextAlign.End,
            )
            if (won) Text("WINNER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SportsCombatRules.winner)
        }
        if (!trailing) SportsPlayerPortrait(athlete.name, SportsCombatRules.portrait(page.game.league, athlete), athlete, 60.dp)
    }
}

