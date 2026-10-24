package com.strmr.tv.ui.sports.game.rugby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsPlay
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors

/** Which rugby events a timeline lists. */
object SportsRugbyEvents {
    fun plays(page: SportsGamePage, substitutions: Boolean): List<SportsPlay> =
        SportsRugby.latest(page.detail.plays.filter(if (substitutions) SportsRugby::isSubstitution else SportsRugby::isKeyEvent))

    fun shows(page: SportsGamePage, substitutions: Boolean): Boolean =
        page.lifecycle != SportsLifecycle.SCHEDULED && (!substitutions || plays(page, substitutions = true).isNotEmpty())
}

/**
 * A rugby match's events (Expo `SportsRugbyTimeline`, tvOS `SportsRugbyTimeline`): tries,
 * conversions, goals and cards ("Scoring & Discipline"), or the [substitutions], newest first by
 * the minute. Six show; Select on "Show All N Events" lists the rest (the chip keeps focus). Each
 * event: minute, mark (T, C, PG, YC…), title, side and player. One stop per event.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsRugbyTimeline(page: SportsGamePage, substitutions: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val plays = SportsRugbyEvents.plays(page, substitutions)
    SportsPanel(if (substitutions) "Substitutions" else "Scoring & Discipline") {
        if (plays.isEmpty()) SportsReadout { Text("The provider has not supplied a scoring timeline.", fontSize = 14.sp, color = SportsMuted) }
        (if (expanded) plays else plays.take(6)).forEach { Event(page, it) }
        if (plays.size > 6) {
            FilterChip(selected = false, onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 10.dp, top = 4.dp)) {
                Text(if (expanded) "Show Fewer Events" else "Show All ${plays.size} Events")
            }
        }
    }
}

@Composable
private fun Event(page: SportsGamePage, play: SportsPlay) {
    val team = page.team(play.teamId)
    val mark = SportsRugby.mark(play.title)
    val markColor = SportsRugby.markColor(mark)
    val who = play.description ?: play.participants.joinToString(", ") { it.name }
    SportsReadout {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(play.clock ?: "—", fontSize = 14.sp, color = SportsMuted, style = SportsDigits, modifier = Modifier.width(35.dp))
            Box(
                Modifier.defaultMinSize(26.dp, 18.dp).clip(RoundedCornerShape(4.dp))
                    .background(markColor ?: team?.let { SportsTeamColors.color(it, page.game).copy(alpha = 0.35f) } ?: Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(mark, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (markColor == null) Color.White else Color.Black)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(play.title.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val detail = listOfNotNull(team?.name, who.ifEmpty { null }).joinToString(" · ")
                if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = SportsMuted)
            }
        }
    }
}

/** The sides' named players (Expo `SportsRugbyLineups`): per side, "number · name · position · Starter|Replacement". One stop per player. */
@Composable
fun SportsRugbyLineups(page: SportsGamePage) {
    SportsPanel("Lineups") {
        page.detail.lineups.forEach { lineup ->
            Text(
                page.team(lineup.teamId)?.name ?: "Team not supplied", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp),
            )
            lineup.players.forEach { player ->
                SportsReadout {
                    Text(
                        listOfNotNull(player.number, player.name, player.position, if (player.starter) "Starter" else "Replacement")
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        fontSize = 14.sp, color = SportsMuted,
                    )
                }
            }
        }
    }
}

/** Whether a lineup has players. */
fun sportsRugbyLineupsShow(page: SportsGamePage): Boolean = page.detail.lineups.any { it.players.isNotEmpty() }
