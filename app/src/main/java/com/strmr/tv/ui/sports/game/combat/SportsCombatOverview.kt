package com.strmr.tv.ui.sports.game.combat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsAccent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsPlayerPortrait
import java.time.Instant

/**
 * The Overview of UFC bouts and boxing cards (Expo `SportsCombatOverview`, tvOS
 * `SportsCombatOverview`): the fighters with their portraits and records, the division, scheduled
 * rounds, result, when and where, the tale of the tape and the fight statistics, beside the whole
 * fight card (each other bout opens its own page). With scores [hidden] (the spoiler view)
 * records, results and statistics are left out.
 */
@Composable
fun SportsCombatOverview(page: SportsGamePage, hidden: Boolean = false) {
    SportsColumns(fullHeight = true) {
        SportsColumn(1.2f) {
            Matchup(page, hidden)
            if (SportsCombatRules.tape(page).isNotEmpty()) Tape(page)
            if (!hidden && page.detail.comparisons.isNotEmpty()) Statistics(page)
        }
        SportsColumn(1f) { SportsFightCard(page, hidden) }
    }
}

@Composable
private fun Matchup(page: SportsGamePage, hidden: Boolean) {
    val game = page.game
    val named = SportsCombatRules.named(game)
    val rounds = game.combat?.scheduledRounds?.takeIf { it == 3 || it == 5 }
    val dated = game.start != Instant.EPOCH
    val time = listOfNotNull(
        if (dated) SportsGameFormat.startDate(game) else null, if (dated) SportsGameFormat.startClock(game) else null,
        game.venue, game.broadcasts?.joinToString(" · "),
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    val status = when {
        hidden -> "Fight results and statistics hidden"
        page.lifecycle == SportsLifecycle.SCHEDULED -> "Scheduled"
        named -> SportsCombatRules.result(game, hidden = false)
        else -> game.statusDetail?.ifEmpty { null } ?: "Result not supplied"
    }
    SportsPanel(if (named) "Fighter Matchup" else "Fight Information", note = game.combat?.cardName ?: game.title) {
        if (named) {
            SportsReadout(Modifier.semantics { contentDescription = "${game.awayTeam.name} versus ${game.homeTeam.name}" }) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Fighter(page, game.awayTeam, hidden, Modifier.weight(1f))
                    Text("VS", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted, modifier = Modifier.padding(top = 22.dp))
                    Fighter(page, game.homeTeam, hidden, Modifier.weight(1f))
                }
            }
        }
        SportsReadout {
            game.combat?.division?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            rounds?.let { Text("$it scheduled rounds", fontSize = 12.sp, color = SportsMuted) }
            Text(status, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (time.isNotEmpty()) Text(time, fontSize = 12.sp, color = SportsMuted)
            page.coverageNote?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
        }
    }
}

@Composable
private fun Fighter(page: SportsGamePage, athlete: SportsTeam, hidden: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SportsPlayerPortrait(athlete.name, SportsCombatRules.portrait(page.game.league, athlete), athlete, 60.dp)
        Text(athlete.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        athlete.record?.takeIf { !hidden && it.isNotEmpty() }?.let { Text("Current record · $it", fontSize = 12.sp, color = SportsMuted) }
    }
}

/** Both fighters' height, reach, weight and stance side by side (Expo "Tale of the tape"); one stop per row. */
@Composable
private fun Tape(page: SportsGamePage) {
    SportsPanel("Tale of the Tape") {
        Names(page)
        SportsCombatRules.tape(page).forEach { row ->
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.away ?: "—", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(row.label, fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text(row.home ?: "—", fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** The fight's statistics (Expo "Fight statistics"): each row's figures around its name, with bars for comparable totals (the away fighter's in the accent). */
@Composable
private fun Statistics(page: SportsGamePage) {
    SportsPanel("Fight Statistics") {
        Names(page)
        page.detail.comparisons.forEach { row ->
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.away.ifEmpty { "—" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = SportsDigits, modifier = Modifier.weight(1f))
                    Text(row.label, fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(2f))
                    Text(
                        row.home.ifEmpty { "—" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = SportsDigits,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                    )
                }
                SportsCombatRules.barValues(row.label, row.away, row.home)?.let { (a, h) ->
                    val top = maxOf(a, h, 1.0)
                    Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Bar(a / top, SportsAccent, Alignment.CenterEnd, Modifier.weight(1f))
                        Bar(h / top, Color.White, Alignment.CenterStart, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(share: Double, color: Color, alignment: Alignment, modifier: Modifier) {
    Box(modifier.fillMaxHeight().clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
        Box(Modifier.fillMaxWidth(share.toFloat()).fillMaxHeight().align(alignment).clip(CircleShape).background(color))
    }
}

/** The two fighters' names over a side-by-side table (away left, home right). */
@Composable
private fun Names(page: SportsGamePage) {
    Row(Modifier.padding(horizontal = 10.dp)) {
        Text(page.game.awayTeam.name, fontSize = 12.sp, color = SportsMuted, modifier = Modifier.weight(1f))
        Text(page.game.homeTeam.name, fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
