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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsPeriodScore
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsKind

private val SetWidth = 46.dp

/**
 * A tennis match's set scores (Expo `SportsTennisSetScores`, the tennis Overview's line score,
 * tvOS `SportsTennisSetScores`): each player (with the seed) over the games of every set,
 * tiebreak points in superscript, the sets won at the end; the set in play is tinted while live.
 * One stop for the remote.
 */
@Composable
fun SportsTennisSetScores(page: SportsGamePage) {
    val periods = page.detail.periods
    val live = page.lifecycle == SportsLifecycle.LIVE
    val latest = SportsTennis.latestSet(periods)
    SportsPanel("Set Scores", note = if (page.lifecycle == SportsLifecycle.FINAL) "Final" else null) {
        SportsReadout(Modifier.semantics { contentDescription = description(page, periods) }) {
            if (live && latest != null) {
                Text(
                    "Set ${latest + 1} · ${if (page.isStale()) "Last reported" else "Live"}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SportsTennis.accent, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(Modifier.padding(bottom = 4.dp)) {
                Text("Player", fontSize = 12.sp, color = SportsMuted, modifier = Modifier.weight(1f))
                periods.indices.forEach { index ->
                    Text(
                        "Set ${index + 1}", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(SetWidth),
                        color = if (live && index == latest) SportsTennis.accent else SportsMuted,
                    )
                }
                Text("Sets", fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.width(SetWidth))
            }
            page.teams.forEach { player ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                PlayerRow(page, player, periods, if (live) latest else null)
            }
        }
    }
}

@Composable
private fun PlayerRow(page: SportsGamePage, player: SportsTeam, periods: List<SportsPeriodScore>, latest: Int?) {
    val away = player.id == page.game.awayTeam.id
    val last = player.id == page.teams.last().id
    // As tall as the name (a pair takes two lines), every set cell filling that height.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            SportsTennis.name(player), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 3,
            modifier = Modifier.weight(1f).padding(vertical = 7.dp),
        )
        periods.forEachIndexed { index, period ->
            val current = index == latest
            Box(
                Modifier.width(SetWidth).fillMaxHeight().background(if (current) SportsTennis.accent.copy(alpha = 0.08f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                val weight = if (current) FontWeight.Bold else if (SportsTennis.won(period, away)) FontWeight.SemiBold else FontWeight.Normal
                Text(SportsTennis.games(period, away), fontSize = 17.sp, fontWeight = weight, style = SportsDigits)
                if (current && last) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(SportsTennis.accent))
            }
        }
        Box(Modifier.width(SetWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart).width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.14f)))
            Text(player.score ?: "–", fontSize = 19.sp, fontWeight = FontWeight.Bold, style = SportsDigits)
        }
    }
}

private fun description(page: SportsGamePage, periods: List<SportsPeriodScore>): String {
    val players = page.teams.map { player ->
        val away = player.id == page.game.awayTeam.id
        "${SportsTennis.name(player)}: ${periods.joinToString(", ") { SportsTennis.games(it, away) }}. Sets won: ${player.score ?: "unknown"}"
    }
    return (listOf("Set scores") + players).joinToString(". ")
}

/**
 * The set scores under the header's status (the tennis scoreboard, tvOS `SportsTennisHeaderSets`):
 * one compact row per player in display order, games per set with tiebreaks, the set in play in
 * the tennis accent while live.
 */
@Composable
fun SportsTennisHeaderSets(page: SportsGamePage) {
    val periods = page.detail.periods
    val latest = if (page.lifecycle == SportsLifecycle.LIVE) SportsTennis.latestSet(periods) else null
    Column(Modifier.semantics { contentDescription = "Set scores" }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        page.teams.forEach { player ->
            val away = player.id == page.game.awayTeam.id
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEachIndexed { index, period ->
                    Text(
                        SportsTennis.games(period, away), fontSize = 14.sp, style = SportsDigits,
                        fontWeight = if (index == latest || SportsTennis.won(period, away)) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == latest) SportsTennis.accent else Color.White,
                    )
                }
            }
        }
    }
}

/** Whether the header shows the set strip: a started tennis match with set scores, scores shown. */
fun sportsTennisHeaderSetsShow(page: SportsGamePage, spoilers: Boolean): Boolean =
    page.kind == SportsKind.TENNIS && !spoilers && page.lifecycle != SportsLifecycle.SCHEDULED && page.detail.periods.isNotEmpty()
