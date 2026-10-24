package com.strmr.tv.ui.sports.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsFormat
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.tennis.SportsTennis

private val SetCell = 22.dp
private val SetsCell = 30.dp

/**
 * A tennis match on the hub (Expo `SportsTennisCard`, tvOS `SportsTennisCard`): the tournament and
 * round, then both players (with seeds) over the games of each set and the sets won, the set in
 * play tinted while live, and the match's state. Scoreboards rarely carry the set scores, so the
 * card says so; with scores hidden only the names and the state show. Emits the card's top and
 * foot, which the card spreads over its height.
 */
@Composable
fun SportsTennisCard(game: SportsGame, leagueName: String, spoilers: Boolean, stale: Boolean) {
    val scheduled = game.lifecycle == SportsLifecycle.SCHEDULED
    val periods = if (spoilers || scheduled) emptyList() else game.detail?.periods.orEmpty()
    val sets = !spoilers && !scheduled && (game.awayTeam.score != null || game.homeTeam.score != null)
    val latest = if (game.lifecycle == SportsLifecycle.LIVE) SportsTennis.latestSet(periods) else null
    val context = SportsTennis.eventContext(game)?.split(" · ").orEmpty()
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SportsCardHeading(leagueName, game.lifecycle)
        context.firstOrNull()?.let { tournament ->
            Column {
                Text(tournament, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (context.size > 1) {
                    Text(context.drop(1).joinToString(" · "), fontSize = 11.sp, color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (periods.isNotEmpty() || sets) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.weight(1f))
                periods.indices.forEach { Label("S${it + 1}", SetCell) }
                if (sets) Label("Sets", SetsCell)
            }
        }
        listOf(game.awayTeam, game.homeTeam).forEachIndexed { index, player ->
            val away = index == 0
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    SportsTennis.name(player).replace("\n", " / "), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp), modifier = Modifier.weight(1f),
                )
                periods.forEachIndexed { set, period ->
                    Box(
                        Modifier.size(SetCell, 20.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (set == latest) Color.White.copy(alpha = 0.16f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            SportsTennis.games(period, away), fontSize = 13.sp, style = SportsDigits,
                            fontWeight = if (set == latest || SportsTennis.won(period, away)) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
                if (sets) {
                    Box(Modifier.width(SetsCell).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Box(Modifier.align(Alignment.CenterStart).width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.16f)))
                        Text(player.score ?: "–", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = SportsDigits)
                    }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val status = status(game, spoilers)
        if (status.isNotEmpty()) Text(status, fontSize = 12.sp, color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!scheduled && !spoilers && periods.isEmpty()) Text("Set scores not provided", fontSize = 11.sp, color = SportsMuted)
        if (stale) SportsCardDelayed()
    }
}

@Composable
private fun Label(text: String, width: Dp) {
    Text(text, fontSize = 10.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.width(width))
}

/** The live state or reported result; a bare "Final" is left to the pill. */
private fun status(game: SportsGame, spoilers: Boolean): String {
    if (game.lifecycle != SportsLifecycle.SCHEDULED && spoilers) return "Scores hidden"
    val line = SportsFormat.statusLine(game, spoilers)
    return if (SportsGameFormat.matches("^(final|postponed|cancell?ed|delayed|tbd)$", line)) "" else line
}
