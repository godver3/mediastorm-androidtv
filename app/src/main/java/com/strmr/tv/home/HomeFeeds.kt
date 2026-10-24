package com.strmr.tv.home

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.episodeCode
import com.strmr.tv.live.epgEnabled
import com.strmr.tv.live.epgNow
import com.strmr.tv.live.liveChannelLists
import com.strmr.tv.live.liveChannels
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Rows built from feeds other than `display-list`: the calendar, the dashboard and the
// favorite live channels.

/**
 * Calendar releases as Home cards: a show's episodes airing together are one card
 * ("S02E01 +2"), as in the Expo app's `groupCalendarItems`.
 */
data class CalendarGroup(val first: CalendarItem, val count: Int = 1) {
    val airs: Long? get() = first.airs

    /** Whether the shelf's `calendarSources` take this release (watchlist only when unset). */
    fun matches(sources: ShelfConfig.CalendarSources?): Boolean {
        if (sources == null) return first.source == "watchlist"
        return when (first.source) {
            "watchlist" -> sources.watchlist == true
            "history" -> sources.history == true
            "trending" -> sources.trending == true
            "top-trending" -> sources.topTrending == true
            "mdblist" -> sources.mdblists == true
            else -> false
        }
    }

    val card: HomeCard
        get() {
            val item = first
            val episode = if (item.seasonNumber != null && item.episodeNumber != null) {
                episodeCode(item.seasonNumber, item.episodeNumber) + if (count > 1) " +${count - 1}" else ""
            } else null
            val release = if (item.isMovie) item.releaseType?.let(::releaseLabel) else episode
            val target = DetailsTarget(
                titleId = item.titleId, name = item.title, isMovie = item.isMovie, year = item.year,
                tmdbId = item.externalIds?.get("tmdb"), imdbId = item.externalIds?.get("imdb"), tvdbId = item.externalIds?.get("tvdb"),
                posterUrl = item.posterUrl, textPosterUrl = item.textPosterUrl, backdropUrl = item.backdropUrl,
                logoUrl = item.logo?.url, overview = item.overview,
            )
            return HomeCard(
                id = "${item.titleId}@${item.airDate}",
                link = HomeLink.Details(target),
                title = item.title,
                subtitle = listOfNotNull(release, dayLabel).joinToString(" · "),
                overview = if (count == 1) item.episodeOverview ?: item.overview else item.overview,
                backdropUrl = item.backdropUrl,
                logoUrl = item.logo?.url,
                artworkUrl = item.backdropUrl ?: item.posterUrl,
                posterUrl = target.displayPosterUrl,
                // A day's several episodes keep only the bulk sheet.
                episode = if (count == 1) item.cardEpisode else null,
            )
        }

    /** "Today 9:00 PM", "Tomorrow", "Tue, Oct 14". */
    private val dayLabel: String
        get() {
            val at = airs ?: return first.airDate
            val zone = ZoneId.systemDefault()
            val day = Instant.ofEpochMilli(at).atZone(zone)
            val today = ZonedDateTime.now(zone).toLocalDate()
            val label = when (day.toLocalDate()) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                today.minusDays(1) -> "Yesterday"
                else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
            }
            val time = first.airTime?.let { day.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }
            return listOfNotNull(label, time).joinToString(" ")
        }

    companion object {
        /** Every release, grouped, in the server's order. */
        fun group(items: List<CalendarItem>): List<CalendarGroup> {
            val groups = mutableListOf<CalendarGroup>()
            val index = mutableMapOf<String, Int>()
            for (item in items) {
                if (item.isMovie) {
                    groups += CalendarGroup(item)
                    continue
                }
                val key = listOf(item.titleId, item.airDate, item.airTime ?: "", item.network ?: "").joinToString("|")
                val at = index[key]
                if (at == null) {
                    index[key] = groups.size
                    groups += CalendarGroup(item)
                } else {
                    val current = groups[at].first
                    val earlier = compareValuesBy(item, current, { it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }) < 0
                    groups[at] = CalendarGroup(if (earlier) item else current, groups[at].count + 1)
                }
            }
            return groups
        }

        /** The Coming Up row: the latest of what already aired, then up to four upcoming (`splitCalendarItems`). */
        fun comingUp(groups: List<CalendarGroup>, limit: Int): List<CalendarGroup> {
            if (limit <= 0) return emptyList()
            val now = System.currentTimeMillis()
            val aired = groups.filter { (it.airs ?: Long.MAX_VALUE) <= now }
            val upcoming = groups.filter { (it.airs ?: Long.MIN_VALUE) > now }
            if (aired.isEmpty()) return upcoming.take(limit)
            if (upcoming.isEmpty()) return aired.takeLast(limit)
            val reserved = minOf(4, limit - 1, upcoming.size)
            val recent = minOf(aired.size, limit - reserved)
            return aired.takeLast(recent) + upcoming.take(limit - recent)
        }

        private fun releaseLabel(type: String) = when (type) {
            "premiere" -> "Premiere"
            "theatricalLimited" -> "Limited Theatrical"
            "theatrical" -> "In Theaters"
            "digital" -> "Digital"
            "physical" -> "Physical"
            "tv" -> "TV"
            "streaming" -> "Streaming"
            else -> type.replaceFirstChar { it.uppercase() }
        }
    }
}

/** A stream playing on the server: who is watching, and how far in; null for live channels and streams without IDs. */
fun DashboardStream.card(): HomeCard? {
    if (!liveSourceUrl.isNullOrEmpty()) return null
    val name = title ?: return null
    val ids = externalIds ?: return null
    val movie = mediaType == "movie"
    val titleId = ids["tmdb"]?.let { if (movie) "tmdb:movie:$it" else "tmdb:tv:$it" }
        ?: ids["tvdb"]?.let { if (movie) "tvdb:movie:$it" else "tvdb:$it" } ?: ids["imdb"] ?: return null
    val target = DetailsTarget(titleId, name, movie, year, ids["tmdb"], ids["imdb"], ids["tvdb"], posterUrl, backdropUrl = backdropUrl)
    val episode = if (seasonNumber != null && episodeNumber != null) episodeCode(seasonNumber, episodeNumber) else null
    val who = profileNames?.joinToString(", ")?.ifEmpty { null }
    return HomeCard(
        id = id, link = HomeLink.Details(target), title = name,
        subtitle = listOfNotNull(if (isPaused == true) "Paused" else "Playing", who, episode).joinToString(" · "),
        overview = episodeName, backdropUrl = backdropUrl, artworkUrl = backdropUrl ?: posterUrl, posterUrl = posterUrl,
        progress = ((percentWatched ?: 0.0) / 100).toFloat().coerceIn(0f, 1f),
    )
}

/** A show whose next episode hasn't aired yet. */
val ContinueWatchingItem.isUpcoming: Boolean get() = !isMovie && airs > System.currentTimeMillis()

/** When the next episode airs; 0 when unknown (already watchable). */
val ContinueWatchingItem.airs: Long
    get() = nextEpisode?.let { ReleaseBadge.airTime(it.airDate, it.airDateTimeUTC) } ?: 0L

/**
 * The Favorite Channels row (`live-favorites`), as the Expo Home builds it: favorites minus
 * hidden ones, in favorites order, each showing what is on now when the guide is on.
 */
suspend fun ApiClient.favoriteChannelCards(limit: Int): List<HomeCard> {
    val (favorites, hiddenList) = liveChannelLists()
    val hidden = hiddenList.toSet()
    val wanted = favorites.filterNot { it in hidden }
    if (wanted.isEmpty()) return emptyList()
    val order = wanted.withIndex().associate { it.value to it.index }
    val channels = liveChannels(wanted, favoritesOnly = true, offset = 0, limit = maxOf(limit, wanted.size)).channels
        .filter { it.id in order && it.id !in hidden }
        .sortedBy { order[it.id] }
        .take(limit)
    // Now playing, when the guide is on; a guide failure just leaves channel names.
    val now = mutableMapOf<String, com.strmr.tv.live.EpgProgram>()
    if (epgEnabled()) {
        channels.map { it.guideKey }.chunked(50).forEach { batch ->
            runCatching { epgNow(batch) }.getOrNull()?.forEach { e -> e.current?.let { now[e.channelId] = it } }
        }
    }
    return channels.map { channel ->
        val airing = now[channel.guideKey]?.takeIf { it.isAiring() }
        HomeCard(
            id = "live-channel:${channel.id}",
            link = HomeLink.Channel(channel, airing),
            title = airing?.title ?: channel.name,
            subtitle = if (airing != null) channel.name else channel.group ?: "Live TV",
            overview = airing?.description,
            backdropUrl = airing?.icon,
            // No programme art: the channel logo on a plain tile.
            logoUrl = channel.logoUrl,
            logoTile = airing?.icon == null,
        )
    }
}
