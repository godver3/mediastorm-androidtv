package com.strmr.tv.ui.sports.standings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsStandings
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.sports.SportsFormat
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsLive
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsSpoilers
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsKind
import com.strmr.tv.ui.sports.game.SportsSectionHeading

/**
 * A team competition's details (Expo `SportsLeagueOverview`, wide layout): its standings beside
 * the hub day's games of the league, live first, then upcoming, then results (newest first).
 * A standings row's "Show … Games" narrows the games to that team; Show All Teams undoes it.
 */
@Composable
fun SportsLeagueOverview(league: String) {
    val hub = SportsHub.state
    val standings = remember(league) { SportsStandingsState(league) }
    var team by remember(league) { mutableStateOf<Pair<String, String>?>(null) }
    val all = hub.games.filter { it.league == league }
    val games = all.filter { g -> team == null || g.awayTeam.id == team!!.first || g.homeTeam.id == team!!.first }
        .map { g -> standings.standings?.groups?.let { SportsStandings.withRecords(g, it) } ?: g }
        .sortedWith(fixtureOrder)
    SportsColumns {
        SportsColumn(1.3f) {
            SportsLeagueStandings(
                standings,
                fixtureTeamIds = all.flatMap { listOf(it.awayTeam.id, it.homeTeam.id) }.toSet(),
                selectedTeamId = team?.first,
                onTeamSelect = { team = it.id to it.name },
            )
        }
        SportsColumn(1f) {
            SportsSectionHeading(team?.second ?: "Games", "${hub.day.label} · ${hub.leagueName(league)}")
            team?.let { OutlinedButton(onClick = { team = null }) { Text("Show All Teams") } }
            if (games.isEmpty()) {
                Text(
                    if (team != null) "No games for this team in the current selection. Choose another day or show all teams."
                    else "No games in this selection. The league table remains available.",
                    fontSize = 13.sp, color = SportsMuted,
                )
            }
            games.forEach { FixtureRow(it) }
        }
    }
}

/** Live first, then upcoming by start, then results newest first. */
private val fixtureOrder = Comparator<SportsGame> { a, b ->
    fun priority(g: SportsGame) = when (g.lifecycle) {
        SportsLifecycle.LIVE -> 0
        SportsLifecycle.SCHEDULED -> 1
        else -> 2
    }
    val p = priority(a) - priority(b)
    when {
        p != 0 -> p
        a.lifecycle == SportsLifecycle.FINAL && b.lifecycle == SportsLifecycle.FINAL -> b.start.compareTo(a.start)
        else -> a.start.compareTo(b.start)
    }
}

/** A game of the league (Expo's fixture card): status, each side with its rank, record and score, broadcasts. Opens the game. */
@Composable
private fun FixtureRow(game: SportsGame) {
    val navigator = LocalNavigator.current
    val spoilers = SportsSpoilers.hidden
    val league = SportsHub.state.leagues[game.league]
    val sides = if (SportsKind.of(league, game).awayFirst) listOf(game.awayTeam, game.homeTeam) else listOf(game.homeTeam, game.awayTeam)
    SportsReadout(onClick = { navigator.open(Page.SportsGame(game.id)) }) {
        Text(SportsFormat.statusLine(game, spoilers), fontSize = 12.sp, maxLines = 1,
             color = if (game.lifecycle == SportsLifecycle.LIVE) SportsLive else SportsMuted)
        sides.forEach { Side(it, game, spoilers) }
        game.broadcasts?.joinToString(" · ")?.ifEmpty { null }?.let { Text(it, fontSize = 12.sp, color = SportsMuted, maxLines = 1) }
    }
}

@Composable
private fun Side(team: SportsTeam, game: SportsGame, spoilers: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SportsTeamLogo(team, 28.dp)
        // Name over record, so neither crowds the other out.
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(team.displayRank?.takeIf { !spoilers }?.let { "#$it" }, team.name).joinToString(" "),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            team.record?.takeIf { !spoilers }?.let { Text(it, fontSize = 12.sp, color = SportsMuted, maxLines = 1) }
        }
        if (game.lifecycle != SportsLifecycle.SCHEDULED && !spoilers) {
            Text(team.score ?: "–", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
        }
    }
}
