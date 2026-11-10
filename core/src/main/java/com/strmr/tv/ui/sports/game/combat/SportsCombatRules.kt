package com.strmr.tv.ui.sports.game.combat

import androidx.compose.ui.graphics.Color
import com.strmr.tv.sports.SportsCombatBout
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage

/**
 * Fight wording and rules (Expo `sports-combat-card.ts`, tvOS `SportsCombatRules`): a fighter's
 * portrait, a bout's result line, and which statistics can be compared with bars.
 */
object SportsCombatRules {
    /** Expo's winner tag colour (#b9dccb). */
    val winner = Color(0xFFB9DCCB)

    /** The feed's portrait, or ESPN's MMA headshot for a UFC fighter id (never for boxing ids); team logos are not portraits. */
    fun portrait(league: String, fighter: SportsTeam): String? {
        val logo = fighter.logoUrl
        if (!logo.isNullOrEmpty() && !SportsGameFormat.matches("/teamlogos/", logo)) return logo
        if (league != "ufc" || fighter.id.isEmpty() || !fighter.id.all { it.isDigit() }) return null
        return "https://a.espncdn.com/i/headshots/mma/players/full/${fighter.id}.png"
    }

    /** Both fighters are named (a schedule-only boxing card is not). */
    fun named(game: SportsGame): Boolean = game.awayTeam.name.isNotEmpty() && game.homeTeam.name.isNotEmpty()

    /**
     * "Diego Lopes wins · Round 2 · 4:48", the round and clock while live ("Live bout"), the state
     * otherwise; "Result hidden" with scores hidden.
     */
    fun result(
        lifecycle: SportsLifecycle, away: SportsTeam, home: SportsTeam, detail: String?, period: String?, clock: String?, hidden: Boolean,
    ): String {
        if (hidden) return "Result hidden"
        return when (lifecycle) {
            SportsLifecycle.SCHEDULED -> "Scheduled"
            SportsLifecycle.POSTPONED -> "Postponed"
            SportsLifecycle.CANCELED -> "Canceled"
            SportsLifecycle.DELAYED -> "Delayed"
            SportsLifecycle.LIVE -> listOfNotNull(period, clock).filter { it.isNotEmpty() && !SportsGameFormat.matches("^[-–—]+$", it) }
                .joinToString(" · ").ifEmpty { "Live bout" }
            SportsLifecycle.FINAL -> {
                val winner = if ((away.winner == true) != (home.winner == true)) (if (away.winner == true) away else home) else null
                val reported = detail?.trim()?.takeUnless { it.isEmpty() || SportsGameFormat.matches("^final$", it) }
                listOf(winner?.let { "${it.name} wins" } ?: "Result not supplied", reported, period, clock)
                    .filterNot { it.isNullOrEmpty() }.joinToString(" · ")
            }
        }
    }

    /** The game's own result line. */
    fun result(game: SportsGame, hidden: Boolean): String =
        result(game.lifecycle, game.awayTeam, game.homeTeam, game.statusDetail, game.period, game.clock, hidden)

    /** A bout's line on the fight card (after its division); none with scores hidden. */
    fun result(bout: SportsCombatBout, hidden: Boolean): String? =
        if (hidden) null else result(bout.lifecycle, bout.away, bout.home, null, bout.period, bout.clock, hidden = false)

    /** Comparable totals (control time as seconds); ratios, percentages and missing values get no bars. */
    fun barValues(label: String, away: String, home: String): Pair<Double, Double>? {
        fun parse(value: String): Double? {
            val raw = value.trim()
            if (SportsGameFormat.matches("control time", label)) {
                val parts = raw.split(":")
                if (parts.size != 2 || parts[1].length != 2) return null
                val minutes = parts[0].toIntOrNull() ?: return null
                val seconds = parts[1].toIntOrNull()?.takeIf { it < 60 } ?: return null
                return (minutes * 60 + seconds).toDouble()
            }
            return if (SportsGameFormat.matches("""^\d+(\.\d+)?$""", raw)) raw.toDoubleOrNull() else null
        }
        val a = parse(away) ?: return null
        val b = parse(home) ?: return null
        return a to b
    }

    /** One row of the tale of the tape. */
    data class TapeRow(val label: String, val away: String?, val home: String?)

    /** The tale of the tape: height, reach, weight and stance from both fighters' profiles. */
    fun tape(page: SportsGamePage): List<TapeRow> {
        fun profile(team: SportsTeam) = page.detail.fighterProfiles.firstOrNull { it.id == team.id }
        val away = profile(page.game.awayTeam)
        val home = profile(page.game.homeTeam)
        return listOf("Height", "Reach", "Weight", "Stance").mapNotNull { label ->
            val a = away?.value(label)?.ifEmpty { null }
            val h = home?.value(label)?.ifEmpty { null }
            if (a == null && h == null) null else TapeRow(label, a, h)
        }
    }
}
