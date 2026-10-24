package com.strmr.tv.home

import com.strmr.tv.api.CardEpisode
import com.strmr.tv.api.ImageRef
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/** What Home is built from: the profile's `homeShelves` and the switches around it. */
data class HomeSettings(
    /** Every configured shelf, enabled or not, in the profile's order. */
    val shelves: List<ShelfConfig>,
    /** Cards a row shows before its explore card (`itemCap`). */
    val itemCap: Int = 20,
    /** `exploreCardPosition`: the explore card leads the row unless set to "end". */
    val exploreFirst: Boolean = true,
    val hideWatched: Boolean = false,
    /** Continue Watching leaves out shows whose next episode hasn't aired (My Upcoming has them). */
    val excludeUpcomingFromContinue: Boolean = true,
    /** Card position, card style and sizes (device settings override them). */
    val layout: ProfileHomeLayout = ProfileHomeLayout(),
    val hasAI: Boolean = false,
    /** What leads Home (`tvTopShelfMode`). */
    val topShelfMode: TopShelfMode = TopShelfMode.STANDARD,
    /** The shelf that takes Top 10's place in SHELF mode (`tvTopShelfSourceId`). */
    val topShelfSourceId: String? = null,
    /** The Home view these settings were resolved for (`all`, `movies`, `shows`, `page-…`). */
    val view: String = HomeViews.ALL,
    /** What the view shows: rows built on the client keep only these titles. */
    val filter: HomeMediaFilter = HomeMediaFilter.ALL,
    /** The profile's Home views, merged with the server's. */
    val views: Map<String, HomeViewSettings> = emptyMap(),
) {
    /** The `homeView` query the server's lists take for this view; empty on All. */
    val viewQuery: Map<String, String> get() = if (view == HomeViews.ALL) emptyMap() else mapOf("homeView" to view)
}

enum class TopShelfMode(val raw: String) {
    STANDARD("default"),
    DISABLED("disabled"),
    SHELF("shelf");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: STANDARD
    }
}

/** One release on the profile's calendar (`GET /api/users/{id}/calendar`). */
@Serializable
data class CalendarItem(
    val title: String,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    /** movie | series */
    val mediaType: String = "series",
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    /** `YYYY-MM-DD` */
    val airDate: String,
    /** `HH:MM` local to the viewer's time zone (`tz`). */
    val airTime: String? = null,
    val network: String? = null,
    /** For movies: theatrical, digital, physical… */
    val releaseType: String? = null,
    val posterUrl: String? = null,
    val textPosterUrl: String? = null,
    val backdropUrl: String? = null,
    val textBackdropUrl: String? = null,
    val backdropUrls: List<String>? = null,
    val overview: String? = null,
    val logo: ImageRef? = null,
    val genres: List<String>? = null,
    val runtimeMinutes: Int? = null,
    val year: Int? = null,
    val externalIds: Map<String, String>? = null,
    /** watchlist | history | trending | top-trending | mdblist */
    val source: String? = null,
) {
    val isMovie: Boolean get() = mediaType == "movie"

    /** The release's episode, for a card's Mark as watched; null for a movie or without its numbers. */
    val cardEpisode: CardEpisode?
        get() = if (isMovie || seasonNumber == null || episodeNumber == null) null
        else CardEpisode(seasonNumber, episodeNumber, episodeTitle, itemId = null)

    /** When it airs, in the viewer's time zone; midnight when the time is unknown. */
    val airs: Long?
        get() = runCatching {
            val zone = ZoneId.systemDefault()
            if (airTime != null) {
                LocalDateTime.parse("$airDate $airTime", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(zone)
            } else {
                LocalDate.parse(airDate).atStartOfDay(zone)
            }.toInstant().toEpochMilli()
        }.getOrNull()

    /** The details id, built as the Expo app builds it (`buildCalendarTitleId`). */
    val titleId: String
        get() {
            val ids = externalIds ?: emptyMap()
            return if (isMovie) {
                ids["tmdb"]?.let { "tmdb:movie:$it" } ?: ids["imdb"] ?: ids["tvdb"]?.let { "tvdb:movie:$it" } ?: title
            } else {
                ids["tmdb"]?.let { "tmdb:tv:$it" } ?: ids["tvdb"]?.let { "tvdb:$it" } ?: ids["imdb"] ?: title
            }
        }
}

/** A stream playing on the server (`GET /api/dashboard/shelf`). */
@Serializable
data class DashboardStream(
    val id: String,
    val profileNames: List<String>? = null,
    val percentWatched: Double? = null,
    val isPaused: Boolean? = null,
    /** movie | episode */
    val mediaType: String? = null,
    val title: String? = null,
    val year: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeName: String? = null,
    val externalIds: Map<String, String>? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val liveSourceUrl: String? = null,
)
