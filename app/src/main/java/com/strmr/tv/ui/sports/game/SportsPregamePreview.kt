package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsComparison
import com.strmr.tv.sports.SportsPregame
import com.strmr.tv.sports.SportsPregamePlayer
import com.strmr.tv.sports.SportsPregameTeam
import com.strmr.tv.sports.SportsStat
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsFigure
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo

/**
 * The matchup preview before a game (Expo `SportsPregamePreview`): each side's record and
 * standing, the season comparison, recent form, probable starters and season leaders, and the
 * previous meeting. Season context only; never this game's statistics. Each panel is one stop
 * for the remote, the two sides side by side. [spoilerSafe] (scores hidden) leaves out the
 * game's own records and rankings.
 */
@Composable
fun SportsPregamePreview(page: SportsGamePage, pregame: SportsPregame?, spoilerSafe: Boolean = false) {
    val preview = PregameContext(page, pregame, spoilerSafe)
    if (!preview.hasContext) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SportsSectionHeading("Matchup Preview", preview.note)
        Sides(page.teams.filter { preview.standing(it) }) { StandingPanel(preview, it) }
        pregame?.comparisons?.takeIf { it.isNotEmpty() }?.let { ComparisonPanel(page, it) }
        Sides(page.teams.filter { pregame?.team(it.id)?.recent?.isNotEmpty() == true }) { RecentPanel(preview, it) }
        Sides(page.teams.filter { preview.players(it) }) { team ->
            val context = preview.context(team)
            if (context != null) {
                if (context.probables.isNotEmpty()) PlayersPanel("${team.shortName} · Probable Starter", context.probables)
                if (context.leaders.isNotEmpty()) PlayersPanel("${team.shortName} · Season Leaders", context.leaders)
            }
        }
        val meeting = pregame?.previousMeeting
        val home = meeting?.let { page.team(it.homeTeamId) }
        val away = meeting?.let { page.team(it.awayTeamId) }
        if (meeting != null && home != null && away != null) {
            SportsPanel("Previous Meeting") {
                SportsReadout {
                    Text("${away.name} ${meeting.awayScore} · ${home.name} ${meeting.homeScore}", fontSize = 14.sp)
                    Text("${SportsGameFormat.dateLabel(meeting.date)} · Final", fontSize = 11.sp, color = SportsMuted)
                }
            }
        }
    }
}

/** What the preview knows about the two sides. */
private class PregameContext(val page: SportsGamePage, val pregame: SportsPregame?, val spoilerSafe: Boolean) {
    fun context(team: SportsTeam): SportsPregameTeam? = pregame?.team(team.id)

    /** Record, home and away records, conference record, ranking and the table's figures. */
    fun facts(team: SportsTeam): List<SportsStat> {
        val context = context(team)
        val rank = context?.rank ?: if (spoilerSafe) null else team.displayRank
        return (
            listOf(
                "Record" to (context?.record ?: if (spoilerSafe) null else team.record),
                "Home" to context?.homeRecord,
                "Away" to context?.awayRecord,
                "Conference" to (context?.conferenceRecord ?: if (spoilerSafe) null else team.conferenceRecord),
                "Ranking" to rank?.let { "#$it" },
            ) + context?.standingStats.orEmpty().map { it.label to it.value }
            ).mapNotNull { (label, value) -> value?.ifEmpty { null }?.let { SportsStat(label, it) } }
    }

    fun standing(team: SportsTeam) = facts(team).isNotEmpty() || !context(team)?.standingLabel.isNullOrEmpty()

    fun players(team: SportsTeam) = context(team)?.let { it.probables.isNotEmpty() || it.leaders.isNotEmpty() } == true

    val hasContext: Boolean
        get() = page.teams.any { standing(it) || context(it)?.recent?.isNotEmpty() == true || players(it) } ||
            pregame?.comparisons?.isNotEmpty() == true || pregame?.previousMeeting != null

    /** "2026–27 · Regular season · Season context · Updated Oct 23, 2026 · Updates delayed". */
    val note: String
        get() = listOfNotNull(
            pregame?.seasonLabel?.let { "$it · Season context" } ?: "Team context",
            pregame?.updatedAt?.let { "Updated ${SportsGameFormat.dateLabel(it)}" },
            if (page.isStale()) "Updates delayed" else null,
        ).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** The two sides' panels side by side, one focus group each, in display order. */
@Composable
private fun Sides(teams: List<SportsTeam>, panel: @Composable (SportsTeam) -> Unit) {
    if (teams.isEmpty()) return
    SportsColumns {
        teams.forEach { team -> SportsColumn { panel(team) } }
    }
}

@Composable
private fun StandingPanel(preview: PregameContext, team: SportsTeam) {
    val label = preview.context(team)?.standingLabel?.ifEmpty { null }
    SportsPanel(null) {
        SportsReadout {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportsTeamLogo(team, 24.dp)
                Text(team.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            label?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                preview.facts(team).chunked(3).forEach { row ->
                    Row {
                        row.forEach { SportsFigure(it.label, it.value, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonPanel(page: SportsGamePage, rows: List<SportsComparison>) {
    val (left, right) = page.teams
    SportsPanel("Season Comparison") {
        SportsReadout {
            ComparisonRow(left.shortName, "Season statistics", right.shortName, header = true)
            rows.forEach { row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                ComparisonRow(page.value(row, left), row.label, page.value(row, right))
            }
        }
    }
}

@Composable
private fun ComparisonRow(left: String, label: String, right: String, header: Boolean = false) {
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(left, fontSize = 14.sp, fontWeight = weight, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text(label, fontSize = 11.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text(right, fontSize = 14.sp, fontWeight = weight, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecentPanel(preview: PregameContext, team: SportsTeam) {
    val games = preview.context(team)?.recent.orEmpty()
    SportsPanel("${team.shortName} · Recent Form", note = "Latest completed games first") {
        SportsReadout {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                games.forEach { game ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(
                            game.result, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            color = if (game.result == "W") Color(0xFF34C759) else Color.White, modifier = Modifier.width(22.dp),
                        )
                        Column {
                            Text(listOfNotNull(game.opponent, game.score).joinToString(" · "), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(SportsGameFormat.dateLabel(game.date), fontSize = 11.sp, color = SportsMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayersPanel(title: String, players: List<SportsPregamePlayer>) {
    SportsPanel(title) {
        SportsReadout {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                players.forEach { player ->
                    Column {
                        Text(player.label, fontSize = 11.sp, color = SportsMuted)
                        Text(listOfNotNull(player.name, player.value).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
