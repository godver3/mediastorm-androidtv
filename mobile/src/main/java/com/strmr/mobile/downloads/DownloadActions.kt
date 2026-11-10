package com.strmr.mobile.downloads

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.Season
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.resolveSource
import com.strmr.tv.home.ReleaseBadge
import com.strmr.tv.player.SourceSwitchRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the Download buttons ask for (the iPhone app's `DownloadRequest`): a movie, an episode,
 * or a whole season from one pack. [DownloadActions.pick] opens the source picker for it.
 */
data class DownloadRequest(
    val target: DetailsTarget,
    val episode: Episode? = null,
    val season: Season? = null,
) {
    val heading: String
        get() = when {
            season != null -> "${target.name} · " + if (season.number == 0) "Specials" else "Season ${season.number}"
            episode != null -> "${target.name} · ${episode.code}"
            else -> target.name
        }

    /** The picker's search: "Show S01E02" / "Movie 1999", or "Show S01" for a season pack. */
    val query: String
        get() = season?.let { target.name + " S%02d".format(it.number) } ?: SourceSwitchRules.query(target, episode?.code)
}

/** Download, Download Season: pick a release, resolve it, queue it in [Downloads]. */
object DownloadActions {
    /** The request whose source picker is open ([com.strmr.mobile.ui.downloads.DownloadPickerHost] shows it). */
    val picking = MutableStateFlow<DownloadRequest?>(null)

    fun pick(request: DownloadRequest) {
        picking.value = request
    }

    /** Resolves the picked release and queues it; returns the message to show. */
    suspend fun queue(result: SourceResult, request: DownloadRequest): String {
        request.season?.let { return queueSeason(result, it, request.target) }
        return try {
            val (path, tracks) = ApiClient.resolveSource(result, request.target.titleId, null)
            Downloads.start(request.target, request.episode, path, releaseName(result), result.sizeBytes, tracks)
            "Download started"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "The download couldn't start"
        }
    }

    private fun releaseName(r: SourceResult) = SourceSwitchRules.releaseName(r.title, r.attributes["raw_title"])

    /** Every aired episode from one pack: in one batch resolve, else one by one. */
    private suspend fun queueSeason(result: SourceResult, season: Season, target: DetailsTarget): String {
        val episodes = season.episodes.filter { ReleaseBadge.forEpisode(it.airedDate) == null }
        if (episodes.isEmpty()) return "No episodes to download"
        var queued = 0
        var failed = 0
        var lastError: String? = null
        val release = releaseName(result)
        fun add(ep: Episode, path: String?, size: Long?) {
            if (path.isNullOrEmpty()) {
                failed++
                return
            }
            try {
                Downloads.start(target, ep, path, release, size ?: 0)
                queued++
            } catch (e: DownloadError) {
                failed++
                lastError = e.message
            }
        }
        val batch = try {
            ApiClient.resolveBatch(result, episodes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (batch.isNotEmpty()) {
            batch.forEach { entry ->
                val ep = episodes.firstOrNull { it.seasonNumber == entry.seasonNumber && it.episodeNumber == entry.episodeNumber }
                    ?: return@forEach
                add(ep, entry.resolution?.webdavPath, entry.resolution?.fileSize)
            }
        } else {
            for (ep in episodes) {
                val targeted = result.targeting(ep)
                val path = targeted?.let {
                    try {
                        ApiClient.resolveSource(it, target.titleId, null).first
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                add(ep, path, null)
            }
        }
        return when {
            queued == 0 -> lastError ?: "No episodes could be downloaded"
            failed == 0 -> "Downloading $queued episodes"
            else -> "Downloading $queued episodes; $failed failed"
        }
    }

    /**
     * Settings › Downloads › Delete Watched: finished downloads the profile has watched go
     * when Downloads opens with the server reachable (offline nothing changes).
     */
    suspend fun deleteWatchedIfEnabled() {
        val done = Downloads.completed
        if (!DownloadSettings.deleteWatched || done.isEmpty()) return
        val keys = runCatching { ApiClient.watchedKeys() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        val watched = done.filter { DownloadWatched.isWatched(it, keys) }
        if (watched.isNotEmpty()) Downloads.delete(watched.map { it.id })
    }
}

/** Whether a download is in the profile's watched keys, matched as the iPhone app matches them (pure, for tests). */
object DownloadWatched {
    fun isWatched(item: DownloadItem, keys: Set<String>): Boolean {
        if (item.isMovie) {
            val ids = listOfNotNull(item.titleId, item.tmdbId?.let { "tmdb:movie:$it" }, item.imdbId, item.tvdbId?.let { "tvdb:movie:$it" })
            return ids.any { "movie:${it.lowercase()}" in keys }
        }
        val season = item.seasonNumber ?: return false
        val episode = item.episodeNumber ?: return false
        val series = buildList {
            add(item.titleId)
            item.tvdbId?.let { add("tvdb:series:$it"); add(it) }
            item.tmdbId?.let { add("tmdb:tv:$it"); add(it) }
            item.imdbId?.let { add("imdb:$it"); add(it) }
        }
        val suffix = ":s%02de%02d".format(season, episode)
        val ids = series.map { it + suffix } + listOfNotNull(item.episodeItemId)
        return ids.any { "episode:${it.lowercase()}" in keys }
    }
}
