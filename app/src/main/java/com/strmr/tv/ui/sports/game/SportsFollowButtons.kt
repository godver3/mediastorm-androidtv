package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsTeamFollows
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsTeamFollowing

/**
 * "Follow <team>" / "Following <team>" for each side of a team-sport matchup, in the game page's
 * action row. A toggle saves at once ([SportsTeamFollowing]): the label flips straight away and
 * flips back, with a brief message, when the save fails. The button stays the same element as
 * its label changes, so focus stays on it.
 */
@Composable
fun SportsFollowButtons(game: SportsGame, kind: SportsKind) {
    if (!hasTeams(game, kind)) return
    val hub = SportsHub.state
    // Opened without the hub (its preferences not read yet): read them for the buttons.
    LaunchedEffect(Unit) { if (hub.preferences == null) hub.loadPreferences() }
    val teamIds = hub.teamIds
    listOf(game.awayTeam, game.homeTeam).let { if (kind.awayFirst) it else it.reversed() }.forEach { team ->
        val followed = SportsTeamFollows.isFollowed(teamIds, game.league, team.id)
        OutlinedButton(onClick = { SportsTeamFollowing.toggle(game.league, team.id, team.followName, !followed) }) {
            if (followed) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text("${if (followed) "Following" else "Follow"} ${team.followName}")
        }
    }
}

/** Two teams with ids, in a sport played by teams (not tennis, golf, combat sports or a fight card). */
private fun hasTeams(game: SportsGame, kind: SportsKind): Boolean =
    kind !in setOf(SportsKind.TENNIS, SportsKind.GOLF, SportsKind.MMA, SportsKind.BOXING) && game.combat == null &&
        game.homeTeam.id.isNotEmpty() && game.awayTeam.id.isNotEmpty() && game.league.isNotEmpty()
