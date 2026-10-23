package com.strmr.tv.player

import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.StartPoint
import com.strmr.tv.api.episodeCode
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Everything needed to resolve, play and report progress for one movie or episode. */
data class PlaybackRequest(
    val title: DetailsTarget,
    val episode: EpisodeInfo? = null,
    /** Resume point; null plays from the start. */
    val start: StartPoint? = null,
    /** Backdrops for the stream-start screen, the episode still first. */
    val backdrops: List<String> = emptyList(),
    val logoUrl: String? = null,
    /** The aired episodes after [episode], in order; the player offers the first as Up Next. */
    val following: List<EpisodeInfo> = emptyList(),
) {
    data class EpisodeInfo(
        val season: Int,
        val episode: Int,
        val absolute: Int? = null,
        val name: String? = null,
        /** The server's canonical progress item ID, when the episode came from `/details-bundle`. */
        val itemId: String? = null,
        val imageUrl: String? = null,
        val overview: String? = null,
    ) {
        val code: String get() = episodeCode(season, episode)
    }

    val isEpisode: Boolean get() = episode != null
    val progressMediaType: String get() = if (isEpisode) "episode" else "movie"

    /** Exact start position in seconds (a percentage resume is applied once the duration is known). */
    val startPosition: Double? get() = (start as? StartPoint.Seconds)?.value

    /**
     * Movies report under their title ID; episodes use the server's `itemId` verbatim, or
     * null so the server derives it from series + season + episode.
     */
    val progressItemId: String? get() = if (episode == null) title.titleId else episode.itemId

    /** Identifies what a prequeue resolves: the title, the episode and the start point. */
    val prequeueKey: String
        get() = listOf(title.titleId, episode?.code ?: "", startPosition?.toLong()?.toString() ?: "").joinToString("|")

    /** The next episode from the start, its still leading the loading art; null when there is none. */
    val nextEpisode: PlaybackRequest?
        get() {
            val next = following.firstOrNull() ?: return null
            val art = listOfNotNull(next.imageUrl) + backdrops.filter { it != episode?.imageUrl && it != next.imageUrl }
            return copy(episode = next, start = null, following = following.drop(1), backdrops = art)
        }

    val displayTitle: String get() = episode?.let { "${title.name} · ${it.code}" } ?: title.name
    val displaySubtitle: String? get() = episode?.name

    /**
     * What the server's stream tracker reads off `/api/video/stream` to put this playback on
     * the admin dashboard and match it to its progress heartbeats. Episodes without a server
     * itemId send `{series}:SxxEyy`, as the Expo app does.
     */
    val streamMetadataQuery: Map<String, String>
        get() = buildMap {
            val t = title
            put("mediaType", progressMediaType)
            put("titleId", t.titleId)
            t.tmdbId?.let { put("tmdb", it) }
            t.imdbId?.let { put("imdb", it) }
            t.tvdbId?.let { put("tvdb", it) }
            t.year?.let { put("year", it.toString()) }
            t.posterUrl?.takeIf { it.isNotEmpty() }?.let { put("posterUrl", it) }
            put("title", t.name)
            if (episode != null) {
                put("itemId", episode.itemId ?: "${t.titleId}:${episode.code}")
                put("seriesId", t.titleId)
                put("seriesName", t.name)
                put("seasonNumber", episode.season.toString())
                put("episodeNumber", episode.episode.toString())
                episode.name?.takeIf { it.isNotEmpty() }?.let { put("episodeName", it) }
            } else {
                put("itemId", t.titleId)
                put("movieName", t.name)
            }
        }

    /** The `/history/progress` body for a position in this playback. */
    fun progressReport(position: Double, duration: Double, paused: Boolean, ended: Boolean): JsonObject = buildJsonObject {
        val t = title
        put("mediaType", progressMediaType)
        put("position", position)
        put("duration", duration)
        put("timestamp", Instant.now().toString())
        put("isPaused", paused)
        put("playbackEnded", ended)
        put("externalIds", buildJsonObject {
            put("titleId", t.titleId)
            t.tmdbId?.let { put("tmdb", it) }
            t.imdbId?.let { put("imdb", it) }
            t.tvdbId?.let { put("tvdb", it) }
        })
        // Episodes without a server itemId: the server derives it from seriesId + season + episode.
        progressItemId?.let { put("itemId", it) }
        t.year?.let { put("year", it) }
        if (episode != null) {
            put("seasonNumber", episode.season)
            put("episodeNumber", episode.episode)
            put("seriesId", t.titleId)
            put("seriesName", t.name)
            episode.name?.let { put("episodeName", it) }
        } else {
            put("movieName", t.name)
        }
        t.posterUrl?.let { put("posterUrl", it) }
    }
}
