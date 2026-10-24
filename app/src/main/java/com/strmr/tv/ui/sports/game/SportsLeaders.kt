package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsPlayerStats
import com.strmr.tv.sports.SportsStat
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout

/**
 * The feed's game leaders (Expo `CompactDetailLeaders`): up to four, each with the player's
 * headshot, side and category, and the figure (under the name when it is a long line).
 */
@Composable
fun SportsGameLeaders(page: SportsGamePage) {
    SportsPanel("Game Leaders") {
        page.detail.leaders.take(4).forEach { leader ->
            val long = leader.value.length > 10
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SportsPlayerPortrait(leader.name, leader.headshotUrl, page.teamByAbbreviation(leader.teamAbbreviation), 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(leader.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${leader.teamAbbreviation} · ${leader.label}", fontSize = 11.sp, color = SportsMuted)
                        if (long) Text(leader.value, fontSize = 13.sp)
                    }
                    if (!long) Text(leader.value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * The standout lines of a result from the box score (Expo `SportsResultPerformers`): baseball's
 * top batter (by RBI, then hits) and pitcher (by innings) per side, hockey's goalies (by saves,
 * also while live). Never the provider's own leader rankings.
 */
@Composable
fun SportsResultPerformers(page: SportsGamePage) {
    SportsPanel(if (page.game.league == "mlb") "Batting & Pitching" else "Goalies") {
        page.performers.forEach { entry ->
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SportsPlayerPortrait(entry.player.name, entry.player.headshotUrl, entry.team, 32.dp)
                    Column {
                        Text(entry.player.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.stats.joinToString(" · ") { "${it.value} ${it.label}" }, fontSize = 11.sp, color = SportsMuted)
                    }
                }
            }
        }
    }
}

/** A side's standout player and the figures that make the line. */
data class SportsPerformer(val team: SportsTeam, val player: SportsPlayerStats, val stats: List<SportsStat>)

/** [SportsResultPerformers]' lines, away side first. */
val SportsGamePage.performers: List<SportsPerformer>
    get() {
        val league = game.league
        if (lifecycle != SportsLifecycle.FINAL && !(league == "nhl" && lifecycle == SportsLifecycle.LIVE)) return emptyList()
        if (league != "mlb" && league != "nhl") return emptyList()
        val categories = if (league == "mlb") listOf("Batting", "Pitching") else listOf("Goalie")
        return listOf(game.awayTeam, game.homeTeam).flatMap { team ->
            categories.mapNotNull { category ->
                fun value(player: SportsPlayerStats, label: String) = player.value(label)?.toDoubleOrNull() ?: 0.0
                fun score(player: SportsPlayerStats): Pair<Double, Double> = when (category) {
                    "Batting" -> value(player, "RBI") to value(player, "H")
                    "Pitching" -> value(player, "IP") to 0.0
                    else -> value(player, "SV") to 0.0
                }
                // maxWith keeps the feed's first of equals.
                val best = detail.playerStats.filter { it.teamId == team.id && it.category == category }
                    .maxWithOrNull(compareBy<SportsPlayerStats>({ score(it).first }, { score(it).second }))
                    ?: return@mapNotNull null
                val keys = when (category) {
                    "Batting" -> listOf("H", "AB", "RBI", "HR")
                    "Pitching" -> listOf("IP", "ER", "K")
                    else -> listOf("SV", "SA", "SV%")
                }
                SportsPerformer(team, best, best.stats.filter { it.label in keys })
            }
        }
    }
