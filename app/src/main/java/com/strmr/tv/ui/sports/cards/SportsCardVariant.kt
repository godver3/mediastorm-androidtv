package com.strmr.tv.ui.sports.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsAccent
import com.strmr.tv.ui.sports.SportsAmber
import com.strmr.tv.ui.sports.SportsFormat
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsStatusPill
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsKind
import com.strmr.tv.ui.sports.game.combat.SportsCombatRules

/**
 * Which body a hub game card draws (Expo `CardBody`'s branches, tvOS `SportsCardVariant`): tennis's
 * set grid, a fight's portraits, a field event's leaders (individual golf, a boxing card without
 * named fighters), or the team matchup.
 */
enum class SportsCardVariant {
    TENNIS, COMBAT, FIELD, MATCHUP;

    companion object {
        fun of(game: SportsGame, kind: SportsKind): SportsCardVariant = when {
            kind == SportsKind.TENNIS -> TENNIS
            kind == SportsKind.GOLF && !game.isMatchup -> FIELD
            kind == SportsKind.BOXING && !SportsCombatRules.named(game) -> FIELD
            kind == SportsKind.MMA || kind == SportsKind.BOXING -> COMBAT
            else -> MATCHUP
        }
    }
}

/** A card's top line: the competition and the LIVE / FINAL state. */
@Composable
fun SportsCardHeading(leagueName: String, lifecycle: SportsLifecycle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(leagueName.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted)
        Spacer(Modifier.weight(1f))
        SportsStatusPill(lifecycle)
    }
}

/** The amber "Updates delayed" under a card whose league is serving older data. */
@Composable
fun SportsCardDelayed() {
    Text("Updates delayed", fontSize = 11.sp, color = SportsAmber)
}

/**
 * A logo-less side's mark: a team's short abbreviation ("RCB"), else a player's initials
 * ("Chris Rodesch" → "CR", "J.J. Spaun" → "JS").
 */
val SportsTeam.monogram: String
    get() {
        val short = abbreviation
        if (!short.isNullOrEmpty() && short.length <= 4 && ' ' !in short && '.' !in short) return short
        val words = name.split(' ', '/').filter { it.firstOrNull()?.isLetter() == true }
        val first = words.firstOrNull()?.first() ?: return shortName.take(3)
        val last = if (words.size > 1) words.last().first() else null
        return listOfNotNull(first, last).joinToString("").uppercase()
    }

/**
 * A field event on the hub (Expo `FieldEventCard` for individual golf and schedule-only boxing,
 * tvOS `SportsFieldCard`): the event's title, how it stands, then the top two on the leaderboard
 * with their score and round ("-19 · R4"). Hide Scores keeps the title and drops the standings.
 * Emits the card's top and foot, which the card spreads over its height.
 */
@Composable
fun SportsFieldCard(game: SportsGame, leagueName: String, kind: SportsKind, spoilers: Boolean, stale: Boolean) {
    val rows = if (spoilers || game.lifecycle == SportsLifecycle.SCHEDULED) emptyList() else SportsFieldLeaders.of(game)
    val progress = when {
        game.lifecycle == SportsLifecycle.SCHEDULED -> SportsFormat.statusLine(game, spoilers)
        spoilers -> "Results hidden"
        else -> game.statusDetail?.ifEmpty { null } ?: game.lifecycle.pill.lowercase().replaceFirstChar { it.uppercase() }
    }
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SportsCardHeading(leagueName, game.lifecycle)
        Text(game.displayTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            progress, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (game.lifecycle == SportsLifecycle.LIVE) SportsAccent else Color.White,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rows.isEmpty()) {
            val empty = when {
                spoilers && game.lifecycle != SportsLifecycle.SCHEDULED -> "Hide Scores is on"
                kind == SportsKind.BOXING -> "Schedule coverage · open for details"
                else -> "Leaderboard not yet available"
            }
            Text(empty, fontSize = 11.sp, color = SportsMuted)
        }
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                row.value?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = SportsDigits) }
            }
        }
        if (stale) SportsCardDelayed()
    }
}

/** A field event's leaders: the detail's leaderboard when the scoreboard carries it, else the listed field. */
object SportsFieldLeaders {
    data class Row(val id: String, val name: String, val value: String?)

    fun of(game: SportsGame): List<Row> {
        val board = game.detail?.leaderboard.orEmpty()
        if (board.isNotEmpty()) {
            return board.take(2).map { entry ->
                val value = listOfNotNull(entry.score, entry.latestRound?.let { "R$it" }).joinToString(" · ")
                Row(entry.id, entry.name, value.ifEmpty { null })
            }
        }
        return game.participants.orEmpty().take(2).map { Row(it.id, it.name, it.score) }
    }
}
