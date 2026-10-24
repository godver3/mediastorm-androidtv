package com.strmr.tv.api

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Bulk scope for `history/watched/bulk`: tells Trakt-style providers a whole show or one
 * season changed, so they can use their own bulk calls.
 */
enum class WatchedScope(val value: String) { SHOW("show"), SEASON("season") }

/**
 * Marks a movie watched / unwatched in one `POST /history/watched/bulk`. The server matches
 * existing rows across ID forms, so the item carries the title's ID plus its external IDs.
 */
suspend fun ApiClient.setWatched(watched: Boolean, movie: DetailsTarget) {
    val update = buildJsonObject {
        watchedBase(movie, watched)
        put("mediaType", "movie")
        put("itemId", movie.titleId)
        movie.year?.let { put("year", it) }
    }
    sendRaw("POST", "$profilePath/history/watched/bulk", body = buildJsonArray { add(update) })
}

/** Episodes of `series`; without an `itemId` the server derives one from series, season and episode. */
suspend fun ApiClient.setWatched(watched: Boolean, episodes: List<Episode>, series: DetailsTarget, scope: WatchedScope? = null) {
    if (episodes.isEmpty()) return
    val updates = buildJsonArray {
        for (ep in episodes) add(episodeUpdate(series, watched, ep.itemId, ep.name, ep.seasonNumber, ep.episodeNumber))
    }
    sendRaw("POST", "$profilePath/history/watched/bulk", scope?.let { mapOf("scope" to it.value) } ?: emptyMap(), body = updates)
}

/** One card's episode (Continue Watching, the calendar), from a long press. */
suspend fun ApiClient.setWatched(watched: Boolean, episode: CardEpisode, series: DetailsTarget) {
    val update = episodeUpdate(series, watched, episode.itemId, episode.name, episode.seasonNumber, episode.episodeNumber)
    sendRaw("POST", "$profilePath/history/watched/bulk", body = buildJsonArray { add(update) })
}

private fun episodeUpdate(series: DetailsTarget, watched: Boolean, itemId: String?, name: String?, season: Int, episode: Int) =
    buildJsonObject {
        watchedBase(series, watched)
        put("mediaType", "episode")
        itemId?.let { put("itemId", it) }
        name?.let { put("name", it) }
        put("seriesId", series.titleId)
        put("seriesName", series.name)
        put("seasonNumber", season)
        put("episodeNumber", episode)
    }

private fun JsonObjectBuilder.watchedBase(title: DetailsTarget, watched: Boolean) {
    put("watched", watched)
    put("name", title.name)
    val ids = listOfNotNull(title.tmdbId?.let { "tmdb" to it }, title.imdbId?.let { "imdb" to it }, title.tvdbId?.let { "tvdb" to it })
    if (ids.isNotEmpty()) putJsonObject("externalIds") { ids.forEach { (k, v) -> put(k, v) } }
}

@kotlinx.serialization.Serializable
private data class WatchHistoryRow(val watched: Boolean = false)

/** Whether a movie is marked watched (`GET /history/watched/movie/{id}`; not found is unwatched). */
suspend fun ApiClient.isMovieWatched(movie: DetailsTarget): Boolean = try {
    send<WatchHistoryRow>("GET", "$profilePath/history/watched/movie/${segment(movie.titleId)}", promptOnLock = false, timeoutMs = 10_000).watched
} catch (e: ApiError) {
    if (e.statusCode == 404) false else throw e
}

/**
 * Whether a card's episode is marked watched (`GET /history/watched/episode/{id}`, by its item
 * ID or the series ID plus `:sXXeYY`; not found is unwatched).
 */
suspend fun ApiClient.isEpisodeWatched(episode: CardEpisode, series: DetailsTarget): Boolean {
    val id = episode.itemId ?: "${series.titleId}:s%02de%02d".format(episode.seasonNumber, episode.episodeNumber)
    return try {
        send<WatchHistoryRow>("GET", "$profilePath/history/watched/episode/${segment(id)}", promptOnLock = false, timeoutMs = 10_000).watched
    } catch (e: ApiError) {
        if (e.statusCode == 404) false else throw e
    }
}

/** Drops a movie's playback progress (its resume point); none to drop counts as done. */
suspend fun ApiClient.deleteMovieProgress(movie: DetailsTarget) {
    try {
        sendRaw("POST", "$profilePath/history/progress/delete",
                body = buildJsonObject { put("mediaType", "movie"); put("itemId", movie.titleId) })
    } catch (e: ApiError) {
        if (e.statusCode != 404) throw e
    }
}
