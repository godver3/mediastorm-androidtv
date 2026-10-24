package com.strmr.tv.ui.sports.game.baseball

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsPlayerStats
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.LocalSportsShowPlayer
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsGameTab
import com.strmr.tv.ui.sports.game.SportsPlayerPortrait
import com.strmr.tv.ui.sports.game.SportsPlayerTarget
import com.strmr.tv.ui.sports.game.SportsTeamColors

/**
 * MLB's live at-bat on the Overview (Expo `SportsBaseballOverview`'s at-bat panel with
 * `BaseballMatchup`, tvOS `SportsBaseballAtBat`): the batter, the diamond with the runners over the
 * count, then the pitcher, each with this game's line from the box score (and the pitcher's pitch
 * count). Between at-bats, the reported status instead. Three stops, left to right; a player with
 * a box-score line opens it on Select.
 */
@Composable
fun SportsBaseballAtBat(page: SportsGamePage, state: SportsBaseballState) {
    val stale = page.isStale()
    val title = if (!state.active) "Game Situation" else if (stale) "Last Reported At-Bat" else "At Bat"
    SportsPanel(title) {
        if (state.active) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Card(page, SportsBaseballPlayer.of(page, state, pitcher = false))
                val spoken = listOfNotNull(state.situation?.bases ?: "Base occupancy unavailable", state.count, state.outs).joinToString(" · ")
                Box(Modifier.weight(0.9f)) {
                    SportsReadout(Modifier.semantics { contentDescription = spoken }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SportsBaseballDiamond(state.situation?.occupiedBases, 66.dp)
                            SportsBaseballCount(state.situation, stale)
                        }
                    }
                }
                Card(page, SportsBaseballPlayer.of(page, state, pitcher = true))
            }
        } else {
            SportsReadout { Text(state.status, fontSize = 14.sp, color = SportsMuted) }
        }
    }
}

@Composable
private fun RowScope.Card(page: SportsGamePage, player: SportsBaseballPlayer) {
    val line = player.line
    val target = line?.takeIf { it.stats.isNotEmpty() && SportsGameTab.STATS.isAvailable(page) }
        ?.let { SportsPlayerTarget(it.teamId, it.id, it.category) }
    val show = LocalSportsShowPlayer.current
    Box(Modifier.weight(1f)) {
        if (target != null && show != null) {
            SportsReadout(onClick = { show(target) }) { PlayerCard(page, player, opens = true) }
        } else {
            SportsReadout { PlayerCard(page, player, opens = false) }
        }
    }
}

/** The batter or the pitcher of the at-bat, with their line in this game's box score. */
data class SportsBaseballPlayer(
    val pitcher: Boolean,
    val name: String?,
    val team: SportsTeam?,
    val line: SportsPlayerStats?,
    /** "AB 2 · H 1 · RBI 0" for the batter, "IP 5.0 · K 6 · P 94" for the pitcher. */
    val summary: List<String>,
) {
    companion object {
        fun of(page: SportsGamePage, state: SportsBaseballState, pitcher: Boolean): SportsBaseballPlayer {
            val situation = state.situation
            val id = if (pitcher) situation?.pitcherId else situation?.batterId
            val team = if (pitcher) state.pitching else state.batting
            val category = if (pitcher) "Pitching" else "Batting"
            val line = id?.let { page.detail.playerStats.firstOrNull { it.id == id && it.teamId == team?.id && it.category == category } }
            val summary = (if (pitcher) listOf("IP", "K") else listOf("AB", "H", "RBI")).mapNotNull { key ->
                line?.stats?.firstOrNull { it.label.uppercase() == key || (key == "K" && it.label.uppercase() == "SO") }?.let { "$key ${it.value}" }
            } + listOfNotNull(situation?.pitcherPitchCount?.takeIf { pitcher && it >= 0 }?.let { "P $it" })
            return SportsBaseballPlayer(pitcher, if (pitcher) situation?.pitcher else situation?.batter, team, line, summary)
        }
    }
}

/** A player of the at-bat: role, portrait, name over a stripe in the side's colour, and this game's line. */
@Composable
private fun PlayerCard(page: SportsGamePage, player: SportsBaseballPlayer, opens: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            (if (player.pitcher) "PITCHER" else "BATTER") + if (player.line == null) "" else " · THIS GAME",
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SportsMuted, letterSpacing = 0.5.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SportsPlayerPortrait(player.name ?: "Player", player.line?.headshotUrl, player.team, 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(player.name ?: "Not supplied", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                player.team?.let {
                    Box(Modifier.size(16.dp, 2.dp).clip(CircleShape).background(SportsTeamColors.color(it, page.game)))
                }
            }
        }
        if (player.summary.isNotEmpty()) Text(player.summary.joinToString(" · "), fontSize = 11.sp, color = SportsMuted)
        if (opens) Text("View game stats ›", fontSize = 11.sp, color = SportsMuted)
    }
}
