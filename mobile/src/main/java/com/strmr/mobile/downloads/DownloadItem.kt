package com.strmr.mobile.downloads

import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.TrackSelection
import com.strmr.tv.api.episodeCode
import java.net.URLDecoder
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * One offline download (the iPhone app's `DownloadItem`): what it is (the title's ids, for
 * playback and progress reports offline), where it comes from (the server stream path) and
 * where it is going (a file in [DownloadStorage]). Persisted as JSON by [Downloads], so the
 * Downloads list and offline playback need no server.
 */
@Serializable
data class DownloadItem(
    val id: String,
    val createdAt: Long,
    // The title
    val titleId: String,
    val isMovie: Boolean,
    val name: String,
    val year: Int? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String? = null,
    // The episode (series only)
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeName: String? = null,
    val episodeOverview: String? = null,
    /** The server's canonical progress item ID, from `/details-bundle`. */
    val episodeItemId: String? = null,
    val runtimeMinutes: Int? = null,
    // The source
    val streamPath: String,
    val releaseName: String? = null,
    /** The server's track choice for this file when it was resolved, so offline playback picks the same audio. */
    val tracks: TrackSelection? = null,
    /** Size the indexer reported; replaced by the response's length once known. */
    val expectedBytes: Long = 0,
    val fileName: String,
    // Transfer state
    val status: Status = Status.PENDING,
    val bytesWritten: Long = 0,
    val waitingReason: WaitingReason? = null,
    val errorMessage: String? = null,
    val completedAt: Long? = null,
    /** Automatic retries after transient failures since the last success or manual resume. */
    val retries: Int = 0,
    /** Epoch ms before which a retried download stays queued. */
    val retryAt: Long? = null,
    /** Bytes per second while downloading; not persisted. */
    @Transient val bytesPerSecond: Double = 0.0,
) {
    enum class Status { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED }

    /** Why a pending download isn't running yet. */
    enum class WaitingReason { WIFI, SERVER, RETRY }

    val isEpisode: Boolean get() = !isMovie
    val isActive: Boolean get() = status == Status.PENDING || status == Status.DOWNLOADING || status == Status.PAUSED
    val isCompleted: Boolean get() = status == Status.COMPLETED

    val progress: Float
        get() = if (expectedBytes <= 0) (if (isCompleted) 1f else 0f)
        else (bytesWritten.toDouble() / expectedBytes).toFloat().coerceIn(0f, 1f)

    val episodeCode: String?
        get() = if (seasonNumber != null && episodeNumber != null) episodeCode(seasonNumber, episodeNumber) else null

    /** "Show · S01E02" for an episode, the title for a movie. */
    val displayTitle: String get() = episodeCode?.let { "$name · $it" } ?: name

    /** A row's main line: an episode's name (or code), a movie's title. */
    val rowTitle: String
        get() = if (isMovie) name else episodeName?.ifEmpty { null } ?: episodeCode?.let { "Episode $it" } ?: name

    /** Groups a show's episodes in the list. */
    val showKey: String get() = titleId

    /** The movie, or one episode of a details page. */
    fun matches(titleId: String, season: Int?, episode: Int?): Boolean {
        if (this.titleId != titleId) return false
        return if (isMovie) season == null else seasonNumber == season && episodeNumber == episode
    }

    val target: DetailsTarget
        get() = DetailsTarget(titleId, name, isMovie, year, tmdbId, imdbId, tvdbId, posterUrl, null, backdropUrl, null, overview)

    /** The progress record's item ID: a movie's title ID, an episode's server ID, else `{series}:SxxEyy`. */
    val progressItemId: String get() = if (isMovie) titleId else episodeItemId ?: "$titleId:${episodeCode.orEmpty()}"

    companion object {
        private val containers = setOf("mkv", "mp4", "m4v", "mov", "avi", "ts", "m2ts", "webm", "wmv", "flv", "mpg", "mpeg")

        /** A new record for a title (and episode) at `streamPath`. */
        fun create(
            target: DetailsTarget, episode: Episode?, streamPath: String, releaseName: String?, expectedBytes: Long,
            tracks: TrackSelection? = null, now: Long = System.currentTimeMillis(),
        ): DownloadItem {
            val id = UUID.randomUUID().toString().lowercase()
            return DownloadItem(
                id = id, createdAt = now, titleId = target.titleId, isMovie = target.isMovie, name = target.name,
                year = target.year, tmdbId = target.tmdbId, imdbId = target.imdbId, tvdbId = target.tvdbId,
                posterUrl = target.posterUrl, backdropUrl = target.backdropUrl, overview = target.overview,
                seasonNumber = episode?.seasonNumber, episodeNumber = episode?.episodeNumber,
                episodeName = episode?.name?.ifEmpty { null }, episodeOverview = episode?.overview, episodeItemId = episode?.itemId,
                runtimeMinutes = episode?.runtimeMinutes, streamPath = streamPath, releaseName = releaseName, tracks = tracks,
                expectedBytes = expectedBytes.coerceAtLeast(0), fileName = "$id.${fileExtension(streamPath)}",
            )
        }

        /** The container's extension from the stream path (`mkv` when it has none we know). */
        fun fileExtension(path: String): String {
            val decoded = runCatching { URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
            val last = decoded.substringBefore('?').substringAfterLast('/')
            val ext = last.substringAfterLast('.', "").lowercase()
            return if (ext in containers) ext else "mkv"
        }
    }
}
