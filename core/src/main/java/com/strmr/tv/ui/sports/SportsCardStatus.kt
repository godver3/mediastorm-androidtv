package com.strmr.tv.ui.sports

import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.liveBaseballSituation
import com.strmr.tv.sports.liveBasketballSituation
import com.strmr.tv.sports.liveFootballSituation
import com.strmr.tv.sports.liveHockeySituation
import com.strmr.tv.sports.periodLabel
import com.strmr.tv.sports.side
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsKind
import com.strmr.tv.ui.sports.game.baseball.SportsBaseballState
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/** The small sport mark of a card's context line (tvOS SF Symbols). */
enum class SportsGlyph { FOOTBALL, BASKETBALL, HOCKEY, SOCCER, TROPHY }

/**
 * A live hub card's context line (Expo `MatchFooter` and `BaseballFooter`, tvOS
 * `SportsCardFooter`): MLB's runners with the count and outs, NFL and college football's down and
 * distance with the ball's spot, a basketball or hockey situation when the feed sends one, else
 * the reported status when it names one (a power play, the bonus). None before or after the
 * game, or with scores hidden.
 */
data class SportsCardFooter(val glyph: SportsGlyph? = null, val bases: List<Boolean>? = null, val text: String) {
    companion object {
        fun of(game: SportsGame, kind: SportsKind, spoilers: Boolean): SportsCardFooter? {
            if (spoilers || game.lifecycle != SportsLifecycle.LIVE) return null
            if (game.league == "mlb") return baseball(game)
            game.liveFootballSituation?.let { football ->
                val text = listOfNotNull(football.offense(game)?.shortName, football.downDistance.ifEmpty { null }, football.ballAt)
                if (text.isNotEmpty()) return SportsCardFooter(SportsGlyph.FOOTBALL, text = text.joinToString(" · "))
            }
            game.liveHockeySituation?.takeIf { it.strength.isNotEmpty() }?.let {
                return SportsCardFooter(SportsGlyph.HOCKEY, text = it.strength)
            }
            game.liveBasketballSituation?.let { basketball ->
                val side = game.side(basketball.possession)?.shortName ?: basketball.possession
                val text = listOfNotNull(side.ifEmpty { null }?.let { "$it possession" }, basketball.bonus)
                if (text.isNotEmpty()) return SportsCardFooter(SportsGlyph.BASKETBALL, text = text.joinToString(" · "))
            }
            return reported(game, kind)
        }

        /** Expo `BaseballFooter`: hidden between half-innings and during breaks or delays. */
        private fun baseball(game: SportsGame): SportsCardFooter? {
            val situation = game.liveBaseballSituation ?: return null
            val phase = "${game.periodLabel.orEmpty()} ${game.statusDetail.orEmpty()} ${situation.inning}"
            if (SportsGameFormat.matches("""middle|mid\b|end\b|delay|suspend|final|break""", phase)) return null
            val parts = listOfNotNull(
                if (situation.balls != null && situation.strikes != null) "${situation.balls}–${situation.strikes} count" else null,
                situation.outs?.let(SportsBaseballState::outs),
            )
            val bases = situation.occupiedBases
            if (bases == null && parts.isEmpty()) return null
            return SportsCardFooter(bases = bases, text = parts.joinToString(" · "))
        }

        /** The status text when it carries a situation and isn't just the clock (Expo `MatchFooter`). */
        private fun reported(game: SportsGame, kind: SportsKind): SportsCardFooter? {
            val detail = game.statusDetail?.trim()?.ifEmpty { null } ?: return null
            val pattern = """possession|ball|[1-4](?:st|nd|rd|th)\s*(?:&|and)|power play|bonus|shot clock|penalty|red card"""
            val status = listOfNotNull(game.periodLabel, game.clock).filter { it.isNotEmpty() }
            if (!SportsGameFormat.matches(pattern, detail) || detail == status.joinToString(" · ") ||
                detail == status.joinToString(" ") || detail == game.periodLabel || detail == game.clock
            ) return null
            val glyph = when (kind) {
                SportsKind.FOOTBALL -> SportsGlyph.FOOTBALL
                SportsKind.BASKETBALL -> SportsGlyph.BASKETBALL
                SportsKind.HOCKEY -> SportsGlyph.HOCKEY
                SportsKind.SOCCER -> SportsGlyph.SOCCER
                else -> SportsGlyph.TROPHY
            }
            return SportsCardFooter(glyph, text = detail)
        }
    }
}

/**
 * The card's score-change cue (Expo `useSportsScoreChange` / `SportsCardFrame`): a live card's
 * two scores while they show. A change between refreshes, never the first load and never with
 * scores hidden, lights the card for two seconds.
 */
object SportsScoreChange {
    const val DURATION_MS = 2_000L

    fun key(game: SportsGame, spoilers: Boolean): String? =
        if (spoilers || game.lifecycle != SportsLifecycle.LIVE) null else "${game.awayTeam.score.orEmpty()}|${game.homeTeam.score.orEmpty()}"

    fun changed(old: String?, new: String?): Boolean = old != null && new != null && old != new
}

/** Expo `sportsCardCountdown`: never "live" on the clock alone. */
fun SportsFormat.countdown(start: Instant?, now: Instant): String {
    if (start == null || start == Instant.EPOCH) return "Upcoming"
    val seconds = Duration.between(now, start).toMillis() / 1000.0
    if (seconds <= 0) return "Awaiting start"
    val minutes = ceil(seconds / 60).toInt()
    if (minutes < 60) return "Starts in $minutes min"
    if (minutes < 1440) {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "Starts in ${hours}h" else "Starts in ${hours}h ${rest}m"
    }
    val days = ceil(minutes / 1440.0).toInt()
    return if (days == 1) "Starts in 1 day" else "Starts in $days days"
}
