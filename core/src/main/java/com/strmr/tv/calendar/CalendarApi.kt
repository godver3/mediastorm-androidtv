package com.strmr.tv.calendar

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.LossyListSerializer
import com.strmr.tv.api.SeriesDetails
import com.strmr.tv.api.Title
import com.strmr.tv.home.CalendarItem
import java.util.TimeZone
import kotlinx.serialization.Serializable

@Serializable private data class CalendarPage(
    @Serializable(with = LossyListSerializer::class) val items: List<CalendarItem> = emptyList(),
)

/**
 * The Calendar screen's window (Expo `getCalendar(…, { days: 90, recentDays: 21, tz, source })`):
 * three months ahead and three weeks back, in the device's time zone, all sources unless one is named.
 */
suspend fun ApiClient.calendar(source: CalendarSource): List<CalendarItem> = send<CalendarPage>(
    "GET", "$profilePath/calendar",
    buildMap {
        put("days", "90")
        put("recentDays", "21")
        put("tz", TimeZone.getDefault().id)
        source.raw?.let { put("source", it) }
    },
    timeoutMs = 90_000,
).items

/**
 * The title's details for the panel (Expo `fetchCalendarItemEnrichment`): a movie's from
 * `/api/metadata/movies/details`; a series' from `/api/metadata/series/details`, with the
 * episode's own overview and runtime in place of the show's.
 */
suspend fun ApiClient.calendarEnrichment(item: CalendarItem): CalendarEnrichment {
    val ids = item.externalIds.orEmpty()
    val query = buildMap {
        put("titleId", item.titleId)
        put("name", item.title)
        item.year?.let { put("year", it.toString()) }
        put("userId", profileId)
        ids["tmdb"]?.takeIf { it.isNotEmpty() }?.let { put("tmdbId", it) }
        ids["tvdb"]?.takeIf { it.isNotEmpty() }?.let { put("tvdbId", it) }
    }
    if (item.isMovie) {
        val imdb = ids["imdb"]?.takeIf { it.isNotEmpty() }
        val title = send<Title>("GET", "/api/metadata/movies/details", if (imdb != null) query + ("imdbId" to imdb) else query)
        return title.enrichment()
    }
    val details = send<SeriesDetails>("GET", "/api/metadata/series/details", query)
    val episode = details.seasons.firstOrNull { it.number == item.seasonNumber }
        ?.episodes?.firstOrNull { it.episodeNumber == item.episodeNumber }
    return details.title.enrichment().copy(
        synopsis = episode?.overview?.trim()?.takeIf { it.isNotEmpty() },
        runtimeMinutes = episode?.runtimeMinutes,
    )
}

private fun Title.enrichment() = CalendarEnrichment(
    synopsis = overview,
    backdropUrl = backdrop?.url,
    year = year,
    runtimeMinutes = runtimeMinutes,
    certification = certification,
    genres = genres,
    // The server lists credits in billing order already.
    cast = credits?.cast?.take(8),
)
