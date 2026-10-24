package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsPregame
import com.strmr.tv.sports.SportsPregameTeam
import com.strmr.tv.sports.parseInstant
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.combat.SportsCombatOverview
import java.time.Instant

/**
 * The page with scores hidden (Expo `SportsSpoilerOverview`): the matchup, when and where it is
 * played and on which channels, then the matchup preview cut to what was known before the start.
 * No tabs, scores, records or game stats. Fights keep their card without results.
 */
@Composable
fun SportsSpoilerOverview(page: SportsGamePage) {
    if (page.kind == SportsKind.MMA || page.kind == SportsKind.BOXING) {
        SportsCombatOverview(page, hidden = true)
        return
    }
    val game = page.game
    val matchup = if (game.isMatchup) "${game.awayTeam.name} · ${game.homeTeam.name}" else game.displayTitle
    val facts = listOfNotNull(
        SportsGameFormat.startDate(game), SportsGameFormat.startClock(game), game.venue, game.broadcasts?.joinToString(" · "),
    ).filter { it.isNotEmpty() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SportsPanel(null, Modifier.widthIn(max = 550.dp)) {
            SportsReadout {
                Text(matchup, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                facts.forEach { Text(it, fontSize = 14.sp, color = SportsMuted) }
                Text("Current game scores and stats hidden", fontSize = 11.sp, color = SportsFaint)
            }
        }
        page.detail.pregame?.let { SportsPregamePreview(page, it.spoilerSafe(game.start, game.id), spoilerSafe = true) }
    }
}

/**
 * Only what was known before the game (Expo `sportsSpoilerContext`): a snapshot taken before the
 * [start] keeps its records, standings and comparisons; a later one keeps only earlier results.
 * This game itself ([eventId]) and later meetings never show.
 */
fun SportsPregame.spoilerSafe(start: Instant, eventId: String): SportsPregame {
    fun before(date: String) = parseInstant(date)?.isBefore(start) ?: false
    val historical = updatedAt?.let(::before) ?: false
    return SportsPregame(
        seasonLabel = seasonLabel,
        updatedAt = updatedAt,
        teams = teams.map { team ->
            (if (historical) team else SportsPregameTeam(team.teamId)).copy(recent = team.recent.filter { it.id != eventId && before(it.date) })
        },
        comparisons = if (historical) comparisons else emptyList(),
        previousMeeting = previousMeeting?.takeIf { before(it.date) },
    )
}
