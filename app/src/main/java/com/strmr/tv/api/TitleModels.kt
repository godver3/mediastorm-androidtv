package com.strmr.tv.api

import kotlinx.serialization.Serializable

@Serializable
data class ImageRef(val url: String)

/** A dated release window (theatrical / digital / physical). */
@Serializable
data class Release(val date: String? = null)

/**
 * Server-computed resume decision (seconds; `percent` is 0–100). Offer resume only when
 * `eligible`; a Trakt import may carry only `percent` (position and duration 0).
 */
@Serializable
data class ResumeState(
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val percent: Double = 0.0,
    val eligible: Boolean = false,
) {
    /** Where playback should start, or null when resume isn't on offer. */
    val startPoint: StartPoint?
        get() = when {
            !eligible -> null
            position > 0 -> StartPoint.Seconds(position)
            percent > 0 -> StartPoint.Percent(percent)
            else -> null
        }

    /** 0–1, for progress bars. */
    val fraction: Float get() = (percent / 100).toFloat().coerceIn(0f, 1f)
}

/** A resume point: an exact position, or a percentage to apply once the duration is known. */
sealed interface StartPoint {
    data class Seconds(val value: Double) : StartPoint
    data class Percent(val value: Double) : StartPoint

    val label: String
        get() = when (this) {
            is Seconds -> formatTimestamp(value)
            is Percent -> "${Math.round(value)}%"
        }
}

@Serializable
data class EpisodeRef(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val absoluteEpisodeNumber: Int? = null,
    val title: String? = null,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val image: ImageRef? = null,
    val airDate: String? = null,
    val airDateTimeUTC: String? = null,
    /** Canonical progress / watch item ID; use verbatim, never rebuild it. */
    val itemId: String? = null,
) {
    val code: String get() = episodeCode(seasonNumber, episodeNumber)
}

/**
 * The one episode a card stands for (Continue Watching's next episode, a calendar release),
 * for its long press's single-episode Mark as watched. [itemId] is the server's, when sent.
 */
data class CardEpisode(val seasonNumber: Int, val episodeNumber: Int, val name: String?, val itemId: String?) {
    val code: String get() = episodeCode(seasonNumber, episodeNumber)

    companion object {
        fun from(ref: EpisodeRef) = CardEpisode(ref.seasonNumber, ref.episodeNumber, ref.title, ref.itemId)
    }
}

/** Title metadata as returned by search, lists and the details endpoints. */
@Serializable
data class Title(
    val id: String,
    val name: String,
    val overview: String? = null,
    val year: Int? = null,
    val poster: ImageRef? = null,
    /** The poster with its title text when the server swapped `poster` for a textless one. */
    val textPoster: ImageRef? = null,
    val backdrop: ImageRef? = null,
    /** Alternative backdrops beyond the primary one. */
    val backdrops: List<ImageRef>? = null,
    val logo: ImageRef? = null,
    val mediaType: String = "movie",
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val tvdbId: Long? = null,
    val certification: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String>? = null,
    val status: String? = null,
    val theatricalRelease: Release? = null,
    val homeRelease: Release? = null,
    /** `YYYY-MM-DD` the title first becomes watchable. */
    val releaseDate: String? = null,
    /** released | theatrical | coming_soon | unreleased (newer servers only). */
    val availability: String? = null,
    /** Top-billed cast (details only). */
    val credits: Credits? = null,
    /** Movies: the collection the movie belongs to (details only). */
    val collection: CollectionRef? = null,
    /** none | partial | complete, for the profile that asked (Search, Top 10, list rows). */
    val watchState: String? = null,
    /** Multi-source ratings (IMDb, Rotten Tomatoes, …), hydrated from MDBList when enabled. */
    val ratings: List<Rating>? = null,
) {
    val isMovie: Boolean get() = mediaType == "movie"
}

/**
 * One source's score: [source] is imdb, tmdb, trakt, tomatoes, audience, metacritic or
 * letterboxd; [value] is on that source's [max] scale.
 */
@Serializable
data class Rating(val source: String, val value: Double, val max: Double = 10.0)

@Serializable
data class SeriesDetails(val title: Title, val seasons: List<Season> = emptyList())

@Serializable
data class Season(val id: String, val name: String = "", val number: Int, val episodes: List<Episode> = emptyList())

@Serializable
data class Episode(
    val id: String,
    val name: String = "",
    val overview: String? = null,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val absoluteEpisodeNumber: Int? = null,
    val runtimeMinutes: Int? = null,
    val image: ImageRef? = null,
    val airedDate: String? = null,
    /** Per-user fields, present on `/details-bundle` responses only. */
    val itemId: String? = null,
    val watched: Boolean? = null,
    val resume: ResumeState? = null,
) {
    val code: String get() = episodeCode(seasonNumber, episodeNumber)
}

/** One trailer from `/api/metadata/trailers` or the details bundle's `trailers`. */
@Serializable
data class Trailer(val name: String = "", val url: String, val site: String? = null, val type: String? = null)

/** The server ranks the candidates into `primaryTrailer`; the Expo app plays that, else the first. */
@Serializable
data class TrailerList(val primaryTrailer: Trailer? = null, val trailers: List<Trailer>? = null) {
    val primary: Trailer? get() = primaryTrailer ?: trailers?.firstOrNull()
}

/** `GET /api/users/{id}/details-bundle`: details plus the per-user progress state. */
@Serializable
data class DetailsBundle(
    val seriesDetails: SeriesDetails? = null,
    val movieDetails: Title? = null,
    /** Series: the episode the page offers to play, with its `itemId`. */
    val upNextEpisode: EpisodeRef? = null,
    /** Resume state for the movie, or for `upNextEpisode`. */
    val resume: ResumeState? = null,
    val inWatchlist: Boolean? = null,
    val watched: Boolean? = null,
    val trailers: TrailerList? = null,
    /** More Like This. */
    val similar: List<Title>? = null,
)

@Serializable
data class ContinueWatchingItem(
    val seriesId: String,
    val seriesTitle: String,
    val overview: String? = null,
    val posterUrl: String? = null,
    val textPosterUrl: String? = null,
    val backdropUrl: String? = null,
    /** The backdrop with its title text, and ranked alternates: the launcher's 16:9 art. */
    val textBackdropUrl: String? = null,
    val backdropUrls: List<String>? = null,
    val logoUrl: String? = null,
    val year: Int? = null,
    val externalIds: Map<String, String>? = null,
    /** When the profile last played it (RFC 3339): the launcher's last engagement. */
    val updatedAt: String? = null,
    val nextEpisode: EpisodeRef? = null,
    val percentWatched: Double? = null,
    val resumePercent: Double? = null,
    /** Resume state of the item this row plays (the movie, or `nextEpisode`). */
    val resume: ResumeState? = null,
    val status: String? = null,
    /** The prequeue / prewarm state of the item the row plays ("ready" once resolved). */
    val prequeueStatus: String? = null,
) {
    val isMovie: Boolean get() = seriesId.contains(":movie:")
}

/** A discovery row entry (`display-list?source=trending` and the other title lists). */
@Serializable
data class ListedTitle(val rank: Int? = null, val title: Title)

/** One page of `GET /api/users/{id}/display-list`; the server sorts, filters and pages. */
@Serializable
data class DisplayListPage<T>(
    /** Lossy: a `null` page is empty, a bad item is skipped and logged. */
    @Serializable(with = LossyListSerializer::class) val items: List<T> = emptyList(),
    val total: Int = 0,
    val sourceTotal: Int? = null,
    /** Genre facets (`includeFacets=true`) and first letters present, on sources that filter server-side. */
    val genres: List<String>? = null,
    val alphabetBuckets: List<String>? = null,
)

fun episodeCode(season: Int, episode: Int) = "S%02dE%02d".format(season, episode)

fun formatTimestamp(seconds: Double): String {
    val s = seconds.toLong().coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** A runtime in minutes as "52m" / "1h" / "1h 5m"; null when unknown. */
fun formatRuntime(minutes: Int?): String? {
    val r = minutes?.takeIf { it > 0 } ?: return null
    val h = r / 60
    val m = r % 60
    return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h ${m}m"
}
