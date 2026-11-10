package com.strmr.tv.calendar

import com.strmr.tv.api.CastMember
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.episodeCode
import com.strmr.tv.home.CalendarItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// The Expo app's `calendar-model.ts`, ported: grouping releases into days, the selection
// that survives a reload, and the strings the cards and the detail panel show.

/** What the feed keeps (`CalendarFilter`). */
enum class CalendarFilter { ALL, UPCOMING, RECENT, SERIES, MOVIES }

/** The calendar sources the server can narrow to (`source` query); ALL sends none. */
enum class CalendarSource(val raw: String?) {
    ALL(null), WATCHLIST("watchlist"), HISTORY("history"), TRENDING("trending"), TOP_TRENDING("top-trending"), MDBLIST("mdblist"),
}

/** The TV navigation rail's entries (`CALENDAR_NAV_ITEMS`): each a filter and a source. */
enum class CalendarCategory(val label: String, val filter: CalendarFilter, val source: CalendarSource) {
    ALL("All", CalendarFilter.ALL, CalendarSource.ALL),
    TV("TV", CalendarFilter.SERIES, CalendarSource.ALL),
    MOVIES("Movies", CalendarFilter.MOVIES, CalendarSource.ALL),
    TRENDING("Trending", CalendarFilter.ALL, CalendarSource.TRENDING),
    WATCHLIST("Watchlist", CalendarFilter.ALL, CalendarSource.WATCHLIST),
    TOP("Top", CalendarFilter.ALL, CalendarSource.TOP_TRENDING),
}

/** One day of the feed: its `YYYY-MM-DD` key and releases in air-time order. */
data class CalendarDay(val key: String, val items: List<CalendarItem>)

/** The focused day and release, by key. */
data class CalendarSelection(val dateKey: String?, val itemKey: String?)

/** What the detail panel shows: the release's own fields, then the title's details over them. */
data class CalendarItemDetail(
    val itemKey: String,
    val isMovie: Boolean,
    val title: String,
    val episodeTitle: String? = null,
    /** "S2 E5" for an episode. */
    val episodeLabel: String? = null,
    val synopsis: String,
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
    val runtimeMinutes: Int? = null,
    val certification: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val network: String? = null,
    /** "9:00 PM" when the release has an air time. */
    val airTime: String? = null,
)

/** Fields the title's details add (`CalendarItemEnrichment`); blank ones leave the release's own. */
data class CalendarEnrichment(
    val synopsis: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val runtimeMinutes: Int? = null,
    val certification: String? = null,
    val genres: List<String>? = null,
    val cast: List<CastMember>? = null,
    val network: String? = null,
)

object CalendarModel {
    /** A release's stable key: the title's best ID, the kind, the day, the episode and the release type. */
    fun itemKey(item: CalendarItem): String {
        val ids = item.externalIds.orEmpty()
        val identity = ids["tmdb"].nonBlank()?.let { "tmdb:$it" }
            ?: ids["tvdb"].nonBlank()?.let { "tvdb:$it" }
            ?: ids["imdb"].nonBlank()
            ?: item.title.trim().lowercase()
        return listOf(identity, item.mediaType, item.airDate, item.seasonNumber ?: 0, item.episodeNumber ?: 0, item.releaseType ?: "")
            .joinToString(":")
    }

    /** Releases kept by `filter`, by day (oldest first), each day in air-time order (untimed last). */
    fun groupDays(items: List<CalendarItem>, filter: CalendarFilter, todayKey: String = todayKey()): List<CalendarDay> = items
        .filter { item ->
            if (item.airDate.isEmpty()) return@filter false
            when (filter) {
                CalendarFilter.UPCOMING -> item.airDate >= todayKey
                CalendarFilter.RECENT -> item.airDate < todayKey
                CalendarFilter.SERIES -> item.mediaType == "series"
                CalendarFilter.MOVIES -> item.mediaType == "movie"
                CalendarFilter.ALL -> true
            }
        }
        .sortedWith(compareBy<CalendarItem> { it.airDate }.thenBy { it.airTime ?: "99:99" })
        .groupBy { it.airDate }
        .map { (key, dayItems) -> CalendarDay(key, dayItems) }

    /**
     * The selection to show: the chosen release when it's still there, else the chosen day's
     * first, else the first release of today or later (the last day when everything has aired).
     */
    fun resolveSelection(days: List<CalendarDay>, dateKey: String?, itemKey: String?, todayKey: String = todayKey()): CalendarSelection {
        val day = days.firstOrNull { it.key == dateKey }
        if (day != null) {
            val item = day.items.firstOrNull { itemKey(it) == itemKey } ?: day.items.firstOrNull()
            return if (item != null) CalendarSelection(day.key, itemKey(item)) else CalendarSelection(null, null)
        }
        val fallback = days.firstOrNull { it.key >= todayKey } ?: days.lastOrNull()
        val first = fallback?.items?.firstOrNull() ?: return CalendarSelection(null, null)
        return CalendarSelection(fallback.key, itemKey(first))
    }

    fun findItem(days: List<CalendarDay>, itemKey: String?): CalendarItem? =
        itemKey?.let { key -> days.firstNotNullOfOrNull { day -> day.items.firstOrNull { itemKey(it) == key } } }

    fun detail(item: CalendarItem): CalendarItemDetail = CalendarItemDetail(
        itemKey = itemKey(item),
        isMovie = item.isMovie,
        title = item.title,
        episodeTitle = item.episodeTitle.nonBlank()?.trim(),
        episodeLabel = if (!item.isMovie && (item.seasonNumber ?: 0) > 0 && (item.episodeNumber ?: 0) > 0) {
            "S${item.seasonNumber} E${item.episodeNumber}"
        } else null,
        synopsis = summary(item),
        backdropUrl = item.backdropUrl.nonBlank() ?: item.backdropUrls?.firstOrNull { it.isNotBlank() }
            ?: item.textBackdropUrl.nonBlank() ?: item.posterUrl.nonBlank(),
        posterUrl = item.posterUrl.nonBlank(),
        year = item.year,
        runtimeMinutes = item.runtimeMinutes,
        genres = item.genres.orEmpty(),
        network = item.network,
        airTime = item.airTime?.let(::formatTime),
    )

    /** The details over the release's own fields, skipping blank and empty values. */
    fun merge(detail: CalendarItemDetail, e: CalendarEnrichment): CalendarItemDetail = detail.copy(
        synopsis = e.synopsis.nonBlank() ?: detail.synopsis,
        backdropUrl = e.backdropUrl.nonBlank() ?: detail.backdropUrl,
        year = e.year ?: detail.year,
        runtimeMinutes = e.runtimeMinutes ?: detail.runtimeMinutes,
        certification = e.certification.nonBlank() ?: detail.certification,
        genres = e.genres?.takeIf { it.isNotEmpty() } ?: detail.genres,
        cast = e.cast?.takeIf { it.isNotEmpty() } ?: detail.cast,
        network = e.network.nonBlank() ?: detail.network,
    )

    /** The panel's line under the title: "S2 E5  ·  Released 2024  ·  TV-MA  ·  Airs at 9:00 PM". */
    fun metadataLine(d: CalendarItemDetail): String = listOfNotNull(
        d.episodeLabel,
        d.year?.let { "Released $it" },
        d.certification.nonBlank()?.trim(),
        d.airTime.nonBlank()?.let { "Airs at ${it.trim()}" },
    ).joinToString(SEPARATOR)

    /** The line under a poster: the episode code, or a movie's release type. */
    fun posterMetadata(item: CalendarItem): String = when {
        item.isMovie -> item.releaseType.nonBlank()?.let(::formatReleaseType).orEmpty()
        (item.seasonNumber ?: 0) <= 0 || (item.episodeNumber ?: 0) <= 0 -> "Episode"
        else -> episodeCode(item.seasonNumber!!, item.episodeNumber!!)
    }

    fun summary(item: CalendarItem): String = item.episodeOverview.nonBlank()?.trim()
        ?: item.overview.nonBlank()?.trim()
        ?: item.network.nonBlank()?.let { "Airs on $it." }
        ?: if (item.isMovie) item.releaseType.nonBlank()?.let { "${formatReleaseType(it)} release." } ?: "Movie release."
        else "Episode details will appear when they are available."

    /** What Select opens: the title's details page (the home calendar row's target). */
    fun target(item: CalendarItem): DetailsTarget = DetailsTarget(
        titleId = item.titleId, name = item.title, isMovie = item.isMovie, year = item.year,
        tmdbId = item.externalIds?.get("tmdb"), imdbId = item.externalIds?.get("imdb"), tvdbId = item.externalIds?.get("tvdb"),
        posterUrl = item.posterUrl, textPosterUrl = item.textPosterUrl, backdropUrl = item.backdropUrl,
        logoUrl = item.logo?.url, overview = summary(item),
    )

    // Dates and times, in the device's locale.

    fun todayKey(): String = LocalDate.now().toString()

    /** "Today", "Tomorrow", "Yesterday", else the weekday. */
    fun dayHeading(key: String, today: LocalDate = LocalDate.now()): String {
        val date = parse(key) ?: return key
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        }
    }

    /** "Monday, October 20": a day section's heading. */
    fun fullDayHeading(key: String): String =
        parse(key)?.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())) ?: key

    /** `HH:MM` as the locale shows it ("9:00 PM"); "All day" without one. */
    fun formatTime(time: String?): String {
        if (time.isNullOrEmpty()) return "All day"
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) return time
        return LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
    }

    fun formatReleaseType(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.equals("tv", ignoreCase = true)) "TV" else trimmed.replaceFirstChar { it.uppercaseChar() }
    }

    fun parse(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    const val SEPARATOR = "  ·  "
}

private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
