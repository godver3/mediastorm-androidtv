package com.strmr.tv.ui.sports.game.rugby

import androidx.compose.ui.graphics.Color
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsPlay
import com.strmr.tv.sports.periodLabel
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsKind
import java.time.Instant

/**
 * Rugby wording and event rules (Expo `sports-rugby.ts`, tvOS `SportsRugby`): the match state, a
 * play's short mark, and the scoring, discipline and substitution events newest first.
 */
object SportsRugby {
    /** "Full time", "Second half · 64'", "Half time", the start before kick-off. */
    fun status(page: SportsGamePage): String {
        val game = page.game
        when (page.lifecycle) {
            SportsLifecycle.FINAL -> return "Full time"
            SportsLifecycle.SCHEDULED -> return if (game.start == Instant.EPOCH) "Scheduled" else SportsGameFormat.startClock(game)
            SportsLifecycle.LIVE -> {}
            else -> return page.lifecycle.pill.lowercase().replaceFirstChar { it.uppercase() }
        }
        if (SportsGameFormat.matches("""^(?:ht|half[- ]?time)$""", game.statusDetail?.trim().orEmpty())) return "Half time"
        val period = game.periodLabel.orEmpty()
        val phase = phases[period] ?: period
        return listOf(phase, game.clock.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Live" }
    }

    private val phases = mapOf(
        "1st" to "First half", "2nd" to "Second half", "3rd" to "Extra time · first half", "4th" to "Extra time · second half",
    )

    /** "Rugby union" / "Rugby league". */
    fun code(kind: SportsKind): String = if (kind == SportsKind.RUGBY_LEAGUE) "Rugby league" else "Rugby union"

    /** A try, conversion, goal or card (or any scoring play): the scoring and discipline timeline. */
    fun isKeyEvent(play: SportsPlay): Boolean = play.scoring || SportsGameFormat.matches("try|conversion|goal|card", play.title)

    fun isSubstitution(play: SportsPlay): Boolean = SportsGameFormat.matches("substitut|replacement", play.title)

    /** Newest first by the minute ("64'", "40+2'") when every clock is readable, else as sent. */
    fun latest(plays: List<SportsPlay>): List<SportsPlay> {
        val minutes = plays.map(::minute)
        if (minutes.any { it == null }) return plays
        return plays.indices.sortedWith(compareByDescending<Int> { minutes[it]!! }.thenBy { it }).map { plays[it] }
    }

    private val Minute = Regex("""^(\d+)(?:\+(\d+))?(?:['′]|$)""")

    fun minute(play: SportsPlay): Int? {
        val match = Minute.find(play.clock?.trim().orEmpty()) ?: return null
        return match.groupValues[1].toInt() + (match.groupValues[2].toIntOrNull() ?: 0)
    }

    /** The event's mark (Expo `rugbyPlaySymbol`): T, PT, C, PG, DG, FG, YC, RC, a swap for a substitution, × for a miss. */
    fun mark(title: String): String {
        val text = title.lowercase()
        return when {
            SportsGameFormat.matches("miss|unsuccessful|disallow|no try", text) -> "×"
            "yellow card" in text -> "YC"
            "red card" in text -> "RC"
            SportsGameFormat.matches("substitut|replacement", text) -> "↔"
            "penalty try" in text -> "PT"
            SportsGameFormat.matches("""\btry\b""", text) -> "T"
            "conversion" in text -> "C"
            "penalty goal" in text -> "PG"
            "field goal" in text -> "FG"
            "drop goal" in text -> "DG"
            else -> "•"
        }
    }

    /** Card marks sit on the card's colour. */
    fun markColor(mark: String): Color? = when (mark) {
        "YC" -> Color(0xFFFBBF24)
        "RC" -> Color(0xFFEF4444)
        else -> null
    }
}
