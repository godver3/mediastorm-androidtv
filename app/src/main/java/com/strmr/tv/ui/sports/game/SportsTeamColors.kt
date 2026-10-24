package com.strmr.tv.ui.sports.game

import androidx.compose.ui.graphics.Color
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsTeam
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A matchup's two team colours for bars, markers and charts, kept readable on the dark page and
 * apart from each other (Expo `sportsMatchupColors` / `readableSportsPair`): near-black fills
 * give way to the official alternate, then to grey; clashing pairs try the alternates, then a
 * contrasting fallback for the home side.
 */
object SportsTeamColors {
    /** Scoring drives and plays (Expo's gold). */
    val scoring = Color(0xFFFFDB70)

    /** Two `#rrggbb` colours, one per side. */
    data class HexPair(val away: String, val home: String)

    /** The colour of one side of the page's game. */
    fun color(team: SportsTeam, game: SportsGame): Color {
        val pair = hexPair(game.awayTeam.color, game.homeTeam.color, game.awayTeam.alternateColor, game.homeTeam.alternateColor)
        return parse(if (team.id == game.homeTeam.id) pair.home else pair.away) ?: Color(0xFF46CFFF)
    }

    /** A feed colour (`rrggbb`, with or without '#'), or null. */
    fun parse(hex: String?): Color? {
        val value = normalized(hex) ?: return null
        return Color(0xFF000000 or value.drop(1).toLong(16))
    }

    fun hexPair(away: String?, home: String?, awayAlternate: String? = null, homeAlternate: String? = null): HexPair {
        val first = visible(away, awayAlternate)
        val second = visible(home, homeAlternate)
        if (distinct(first, second)) return HexPair(first, second)
        usable(homeAlternate)?.let { if (distinct(first, it)) return HexPair(first, it) }
        usable(awayAlternate)?.let { alternate ->
            if (distinct(alternate, second)) return HexPair(alternate, second)
            usable(homeAlternate)?.let { other -> if (distinct(alternate, other)) return HexPair(alternate, other) }
        }
        val fallback = listOf("#ffc857", "#46cfff", "#ffffff", "#ff70ac", "#8bea86")
            .filter { distinct(first, it) }
            .maxByOrNull { distance(first, it) }
        return fallback?.let { HexPair(first, it) } ?: HexPair("#46cfff", "#ffc857")
    }

    private fun normalized(hex: String?): String? {
        val value = hex?.trim()?.replace("#", "")?.lowercase() ?: return null
        return if (value.length == 6 && value.toLongOrNull(16) != null) "#$value" else null
    }

    private fun visible(hex: String?, alternate: String?): String {
        val primary = normalized(hex) ?: "#00c8f0"
        return if (!nearBlack(primary)) primary else usable(alternate) ?: "#b7c2cd"
    }

    private fun usable(hex: String?): String? = normalized(hex)?.takeUnless(::nearBlack)

    private fun rgb(hex: String): List<Double> {
        val value = hex.drop(1).toLong(16)
        return listOf((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF).map { it.toDouble() }
    }

    /** Black and near-black fills disappear into the page. */
    private fun nearBlack(hex: String): Boolean {
        val channels = rgb(hex)
        val high = channels.max()
        val low = channels.min()
        return high <= 48 || (high <= 80 && high - low <= 20)
    }

    private fun distance(a: String, b: String): Double = sqrt(rgb(a).zip(rgb(b)).sumOf { (x, y) -> (x - y) * (x - y) })

    private fun luminance(hex: String): Double = rgb(hex).zip(listOf(0.2126, 0.7152, 0.0722)).sumOf { (channel, weight) ->
        val value = channel / 255
        (if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)) * weight
    }

    /** The WCAG contrast ratio of two `#rrggbb` colours (Expo `sportsColorContrast`). */
    fun contrast(a: String, b: String): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    private fun hue(hex: String): Double? {
        val c = rgb(hex).map { it / 255 }
        val high = c.max()
        val low = c.min()
        val delta = high - low
        if (delta < 0.08) return null
        val base = when (high) {
            c[0] -> (c[1] - c[2]) / delta
            c[1] -> (c[2] - c[0]) / delta + 2
            else -> (c[0] - c[1]) / delta + 4
        }
        return (base * 60 + 360) % 360
    }

    fun distinct(a: String, b: String): Boolean {
        if (distance(a, b) < 110) return false
        val x = hue(a) ?: return true
        val y = hue(b) ?: return true
        val gap = min(abs(x - y), 360 - abs(x - y))
        return gap >= 30 || contrast(a, b) >= 2.5
    }
}
