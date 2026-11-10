package com.strmr.tv.ui.sports.game

import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.parseInstant
import com.strmr.tv.sports.periodLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * The game page's wording of a game's state (Expo `formatDetailStatus`, `formatGameResult`,
 * `getSportsPregameState` and `formatSportsFreshness`).
 */
object SportsGameFormat {
    private val day = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val date = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val stamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** "Fri, Oct 23". */
    fun startDate(game: SportsGame): String = day.format(game.start.atZone(zone))

    /** "2:00 PM". */
    fun startClock(game: SportsGame): String = clock.format(game.start.atZone(zone))

    /** "Oct 21, 2026" for an RFC 3339 date; empty when it can't be read. */
    fun dateLabel(value: String): String = parseInstant(value)?.let { date.format(it.atZone(zone)) }.orEmpty()

    /**
     * The scoreboard's state line: hockey periods and overtime, soccer halves and extra time,
     * cricket's reported state, the period and clock while live, the start before it.
     */
    fun detailStatus(page: SportsGamePage, stale: Boolean = false): String {
        val game = page.game
        val lifecycle = game.lifecycle
        val period = game.periodLabel.orEmpty()
        val clock = game.clock.takeIf {
            (lifecycle == SportsLifecycle.LIVE && game.league != "mlb") || (lifecycle == SportsLifecycle.FINAL && page.kind == SportsKind.MMA)
        }
        val detail = game.statusDetail.orEmpty()
        val delayed = if (stale) "Updates delayed" else null
        val live = lifecycle == SportsLifecycle.LIVE

        if (page.kind == SportsKind.CRICKET && lifecycle != SportsLifecycle.SCHEDULED) {
            val reported = listOf(detail, period).map { it.trim() }.firstOrNull { it.isNotEmpty() }
            return join(reported ?: fallback(lifecycle), delayed)
        }
        if (game.league == "nhl" && (live || lifecycle == SportsLifecycle.FINAL)) {
            val supplied = "$period $detail"
            val shootout = matches("""\bSO\b|shootout""", supplied)
            val overtime = matches(OVERTIME, supplied)
            val intermission = matches("""intermission|\bINT\b""", supplied)
            if (!live) return join("Final", if (shootout) "Shootout" else if (overtime) "Overtime" else null, delayed)
            val label = when {
                shootout -> "Shootout"
                intermission -> "Intermission"
                overtime -> if (matches("""\d+OT|OT\d+""", period)) "Overtime · $period" else "Overtime"
                else -> hockeyPeriods[period] ?: period
            }
            return join(label.ifEmpty { "Live" }, if (shootout || intermission) null else clock, delayed)
        }
        if (page.kind == SportsKind.SOCCER && (live || lifecycle == SportsLifecycle.FINAL)) {
            if (!live) {
                val supplied = "$period $detail"
                val shootout = game.awayTeam.shootoutScore != null || game.homeTeam.shootoutScore != null
                val status = when {
                    matches("""\b(?:PEN|PK)\b|penalt""", supplied) || shootout -> "Full time · Penalties"
                    matches("""\b(?:ET[12]?|AET)\b|extra time""", supplied) -> "Full time · After extra time"
                    else -> "Full time"
                }
                return join(status, delayed)
            }
            val stopped = matches("""^(HT|halftime|half time|PEN|PK)$""", period)
            val status = join(soccerPeriods[period.uppercase()] ?: period, if (stopped) null else clock)
            return join(status.ifEmpty { "Live" }, delayed)
        }
        return when (lifecycle) {
            SportsLifecycle.LIVE -> {
                val collegeOvertime = game.league == "college-football" && matches(OVERTIME, period)
                join(period, if (collegeOvertime) null else clock).ifEmpty { "Live" }
            }
            SportsLifecycle.SCHEDULED -> join(startDate(game), startClock(game))
            else -> detail.ifEmpty { join(lifecycle.pill, period) }
        }
    }

    /** "Chicago Bears win", "… win 4–3 on penalties", or "Draw" for a level soccer final. */
    fun result(page: SportsGamePage): String? {
        val game = page.game
        if (game.lifecycle != SportsLifecycle.FINAL) return null
        val away = game.awayTeam
        val home = game.homeTeam
        if ((away.winner == true) != (home.winner == true)) {
            val (winner, loser) = if (away.winner == true) away to home else home to away
            val won = winner.shootoutScore
            val lost = loser.shootoutScore
            return if (won != null && lost != null) "${winner.name} win $won–$lost on penalties" else "${winner.name} win"
        }
        val level = !away.score.isNullOrEmpty() && away.score == home.score
        return if (page.kind == SportsKind.SOCCER && level && away.shootoutScore == null && home.shootoutScore == null) "Draw" else null
    }

    /**
     * Within 15 minutes of a scheduled start (Expo `SportsPregameNotice`): "Starts in 12:04"
     * counting down, then "Starting soon" once the start has passed.
     */
    fun pregameCountdown(game: SportsGame, now: Instant = Instant.now()): String? {
        if (game.lifecycle != SportsLifecycle.SCHEDULED || game.start == Instant.EPOCH) return null
        val remaining = Duration.between(now, game.start).toMillis() / 1000.0
        if (remaining < 0) return "Starting soon"
        if (remaining > 15 * 60) return null
        val seconds = ceil(remaining).toInt()
        return "Starts in ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    /** The page's footer (Expo `formatSportsFreshness`). */
    fun freshness(page: SportsGamePage, stale: Boolean): String {
        if (stale) return "Scores may be delayed"
        val updated = page.detail.updatedAt?.let(::parseInstant) ?: return "Update time unavailable"
        return "Feed checked ${stamp.format(updated.atZone(zone))}"
    }

    /** A case-insensitive search for [pattern] in [text]. */
    fun matches(pattern: String, text: String): Boolean =
        patterns.getOrPut(pattern) { Regex(pattern, RegexOption.IGNORE_CASE) }.containsMatchIn(text)

    private val patterns = ConcurrentHashMap<String, Regex>()

    private const val OVERTIME = """\b(?:\d+OT|OT\d*)\b|overtime"""

    private fun join(vararg parts: String?) = parts.filterNot { it.isNullOrEmpty() }.joinToString(" · ")

    private fun fallback(lifecycle: SportsLifecycle) = when (lifecycle) {
        SportsLifecycle.LIVE -> "Live"
        SportsLifecycle.FINAL -> "Final"
        else -> lifecycle.pill.lowercase().replaceFirstChar { it.uppercase() }
    }

    private val hockeyPeriods = mapOf("P1" to "1st period", "P2" to "2nd period", "P3" to "3rd period")

    private val soccerPeriods = mapOf(
        "HT" to "Halftime", "H1" to "First half", "H2" to "Second half", "ET1" to "Extra time · First half",
        "ET2" to "Extra time · Second half", "ET" to "Extra time", "PEN" to "Penalty shootout", "PK" to "Penalty shootout",
        "FT" to "Full time",
    )
}
