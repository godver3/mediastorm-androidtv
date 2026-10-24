package com.strmr.tv.ui.sports.game.soccer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLineup
import com.strmr.tv.sports.SportsLineupPlayer
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsChipRow
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGamePage

/**
 * The soccer sides' players (Expo `SportsLineupsPanel`, wide; tvOS `SportsLineupLists`): starters,
 * substitutes or the reported changes, both sides next to each other with their formation, each
 * player's number, name, position and whether they came on or went off. Under the pitch on the
 * Lineups tab.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsLineupLists(page: SportsGamePage) {
    var selection by remember { mutableStateOf(LineupView.STARTERS) }
    val chipFocus = remember { FocusRequester() }
    val lineups = page.detail.lineups
    val hasChanges = lineups.any { lineup -> lineup.players.any { it.subbedIn || it.subbedOut } }
    val choices = if (hasChanges) LineupView.entries else listOf(LineupView.STARTERS, LineupView.BENCH)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SportsChipRow(chipFocus) {
            choices.forEach { choice ->
                FilterChip(
                    selected = selection == choice, onClick = { selection = choice },
                    modifier = if (selection == choice) Modifier.focusRequester(chipFocus) else Modifier,
                ) { Text(choice.label) }
            }
        }
        SportsColumns {
            page.teams.forEach { team -> SportsColumn { TeamList(team, lineups.firstOrNull { it.teamId == team.id }, selection) } }
        }
        Text("Reported match selection · Starting roles remain unchanged after substitutions", fontSize = 11.sp, color = SportsMuted)
    }
}

private enum class LineupView(val label: String, val heading: String) {
    STARTERS("Starters", "Starting XI"), BENCH("Substitutes", "Substitutes"), CHANGES("Changes", "Reported changes");

    fun includes(player: SportsLineupPlayer) = when (this) {
        STARTERS -> player.starter
        BENCH -> !player.starter
        CHANGES -> player.subbedIn || player.subbedOut
    }
}

@Composable
private fun TeamList(team: SportsTeam, lineup: SportsLineup?, selection: LineupView) {
    val players = lineup?.players.orEmpty().filter(selection::includes)
    SportsPanel(null) {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportsTeamLogo(team, 24.dp)
                Text(team.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(lineup?.formation?.let { "Formation · $it" } ?: "Formation unavailable", fontSize = 12.sp, color = SportsMuted)
            Text("${selection.heading} · ${if (players.size == 1) "1 player" else "${players.size} players"}", fontSize = 12.sp, color = SportsMuted)
        }
        if (players.isEmpty()) {
            SportsReadout {
                Text(
                    if (selection == LineupView.CHANGES) "No substitution changes supplied for this team." else "This selection is not available from the feed.",
                    fontSize = 14.sp, color = SportsMuted,
                )
            }
        }
        players.forEach { player ->
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(player.number ?: "–", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(player.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(player.position ?: "Position unavailable", fontSize = 11.sp, color = SportsMuted)
                    }
                    val changes = listOfNotNull(if (player.subbedIn) "↑ Came on" else null, if (player.subbedOut) "↓ Went off" else null)
                    if (changes.isNotEmpty()) Text(changes.joinToString(" · "), fontSize = 11.sp, color = Accent)
                }
            }
        }
    }
}
