package com.strmr.tv.ui.sports

import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object SportsFormat {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val day = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val fullDay = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

    /** "7:05 PM · Starts in 2h 5m" (Expo's countdown), the live clock, or the final line. */
    fun statusLine(game: SportsGame, spoilers: Boolean, now: Instant = Instant.now()): String = when (game.lifecycle) {
        SportsLifecycle.SCHEDULED -> {
            val countdown = countdown(game.start, now)
            if (game.start == Instant.EPOCH) countdown else "${time.format(game.start.atZone(ZoneId.systemDefault()))} · $countdown"
        }
        SportsLifecycle.LIVE -> if (spoilers) "In progress · scores hidden"
            else game.statusDetail ?: listOfNotNull(game.period, game.clock).joinToString(" · ")
        SportsLifecycle.FINAL -> if (spoilers) "Result hidden" else game.statusDetail ?: "Final"
        else -> game.statusDetail ?: startTime(game)
    }

    fun startTime(game: SportsGame): String {
        val local = game.start.atZone(ZoneId.systemDefault())
        val today = java.time.LocalDate.now()
        return if (local.toLocalDate() == today) time.format(local) else "${day.format(local)} · ${time.format(local)}"
    }

    fun date(game: SportsGame): String = fullDay.format(game.start.atZone(ZoneId.systemDefault()))
}
