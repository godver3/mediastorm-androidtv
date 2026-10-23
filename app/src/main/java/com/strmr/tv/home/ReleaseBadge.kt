package com.strmr.tv.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A card badge: a release status, a rank or Watched. Statuses carry a symbol that artwork
 * shows instead of the text (the text stays as the content description and in the details header).
 */
data class CardBadge(val text: String, val symbol: Symbol? = null) {
    enum class Symbol { COMING_SOON, UNRELEASED, IN_THEATERS, WATCHED }

    companion object {
        val ComingSoon = CardBadge("Coming Soon", Symbol.COMING_SOON)
        val Unreleased = CardBadge("Unreleased", Symbol.UNRELEASED)
        val InTheaters = CardBadge("In Theaters", Symbol.IN_THEATERS)
        val Watched = CardBadge("Watched", Symbol.WATCHED)
    }
}

/** "Not out yet" labels, from the backend's release availability and air dates. */
object ReleaseBadge {
    /** Window in which a not-yet-released title counts as "Coming Soon" (the server uses the same). */
    private const val COMING_SOON_MS = 30L * 24 * 3600 * 1000

    /** Title-level badge from the server's `availability`; older servers send only `status`. */
    fun forTitle(availability: String?, status: String?, releaseDate: String?): CardBadge? = when (availability) {
        "coming_soon" -> CardBadge.ComingSoon
        "unreleased" -> CardBadge.Unreleased
        "theatrical" -> CardBadge.InTheaters
        "released" -> null
        else -> forStatus(status, parseDay(releaseDate))
    }

    /** Legacy badge from the backend's normalized `status`. */
    fun forStatus(status: String?, releaseDate: Long? = null): CardBadge? = when (status?.lowercase()) {
        "upcoming", "unreleased" ->
            if (releaseDate != null && releaseDate - System.currentTimeMillis() <= COMING_SOON_MS) CardBadge.ComingSoon else CardBadge.Unreleased
        "theatrical" -> CardBadge.InTheaters
        else -> null
    }

    fun parseDay(value: String?): Long? {
        if (value == null || value.length < 10) return null
        return runCatching { LocalDate.parse(value.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    }

    /**
     * An episode's air date for the season's episode list, "Oct 20, 2026", aired or not
     * (Expo `TVEpisodeStrip.formatAirDate`: the date part, en-US short month, day, year).
     */
    fun airDateLabel(airDate: String?, locale: Locale = Locale.getDefault()): String? {
        if (airDate == null || airDate.length < 10) return null
        val day = runCatching { LocalDate.parse(airDate.take(10)) }.getOrNull() ?: return null
        return day.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    }

    /** When an episode airs: the exact UTC time, else local midnight of its day. */
    fun airTime(airDate: String?, airDateTimeUTC: String?): Long? =
        airDateTimeUTC?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: parseDay(airDate)

    /** Episode badge while the air time is still in the future ("Airs Tomorrow"). */
    fun forEpisode(airDate: String?, airDateTimeUTC: String? = null): String? {
        val airs = airTime(airDate, airDateTimeUTC) ?: return null
        if (airs <= System.currentTimeMillis()) return null
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(airs).atZone(zone)
        val today = ZonedDateTime.now(zone).toLocalDate()
        return when (day.toLocalDate()) {
            today -> if (airDateTimeUTC != null) "Airs Today ${day.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}" else "Airs Today"
            today.plusDays(1) -> "Airs Tomorrow"
            else -> "Airs " + day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "MMM d" else "MMM d, yyyy", Locale.getDefault()))
        }
    }
}
