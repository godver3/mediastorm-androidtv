package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ThumbnailStartOptions
import com.strmr.tv.api.VideoThumbnailStatus
import com.strmr.tv.api.previewThumbnailsEnabled
import com.strmr.tv.api.startVideoThumbnails
import com.strmr.tv.api.thumbnailImageUrl
import com.strmr.tv.api.videoThumbnailsStatus
import com.strmr.tv.app.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Seek-preview thumbnails for one playback, from the backend's thumbnail API (Seekr's pre-made
 * set first, the server's own ffmpeg generation as the fallback), as the tvOS app and the Expo
 * player do: read the status straight away (cached sets come back at once), ask for a
 * Seekr-only start, and only if Seekr misses ask the server to generate them, which reads the
 * source; on a WebDAV (usenet) path that waits until playback has run 15 s and isn't
 * buffering. Polls every 2 s while pending.
 */
class PlaybackThumbnails(private val path: String, private val request: PlaybackRequest) {
    data class Thumbnail(val timeMs: Long, val url: String)

    /** Session facts the start request needs, re-read on every attempt. */
    data class Context(val durationMs: Long, val positionMs: Long, val buffering: Boolean, val hdr: Boolean, val dolbyVision: Boolean)

    private val _thumbnails = MutableStateFlow<List<Thumbnail>>(emptyList())
    /** Times ascending. */
    val thumbnails: StateFlow<List<Thumbnail>> = _thumbnails

    private var job: Job? = null

    fun start(scope: CoroutineScope, context: () -> Context) {
        if (job != null) return
        job = scope.launch {
            if (!ApiClient.previewThumbnailsEnabled()) return@launch
            run(context)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    /** The thumbnail at or just before `timeMs` (the frame a seek there lands near). */
    fun at(timeMs: Long): Thumbnail? {
        val list = _thumbnails.value
        if (list.isEmpty()) return null
        var lo = 0
        var hi = list.size - 1
        if (timeMs <= list[0].timeMs) return list[0]
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (list[mid].timeMs <= timeMs) lo = mid else hi = mid - 1
        }
        return list[lo]
    }

    /** Neighbours of `timeMs`, for prefetching while the scrub moves. */
    fun around(timeMs: Long, count: Int = 2): List<Thumbnail> {
        val list = _thumbnails.value
        val center = at(timeMs)?.let { list.indexOf(it) } ?: return emptyList()
        return (center - count..center + count).filter { it in list.indices && it != center }.map { list[it] }
    }

    private suspend fun run(context: () -> Context) {
        var seekrRequested = false
        var generationRequested = false
        var lastStatus = ""
        val pressureSensitive = path.startsWith("/webdav/") || path.startsWith("webdav/")

        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val status = try {
                ApiClient.videoThumbnailsStatus(path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "status failed: ${e.message}")
                delay(2_000)
                continue
            }
            val count = status.thumbnails?.size ?: 0
            if (status.status != lastStatus) {
                AppLog.i(TAG, "status ${status.status} count=$count")
                lastStatus = status.status
            }
            // An empty transient answer must not erase thumbnails already shown.
            if (count > 0) apply(status.thumbnails.orEmpty())

            val ctx = context()
            if (status.status == "disabled") return
            if (status.status == "ready" && count > 0) return
            // Generation was asked for and settled without a usable set (failed).
            if (generationRequested && status.status != "generating" && status.status != "pending") return
            val canGenerate = !pressureSensitive || (ctx.positionMs >= 15_000 && !ctx.buffering)
            if (status.status != "generating" && !generationRequested && ctx.durationMs >= 120_000 &&
                (!seekrRequested || canGenerate)
            ) {
                val seekrOnly = !seekrRequested
                if (seekrOnly) seekrRequested = true else generationRequested = true
                try {
                    val started = ApiClient.startVideoThumbnails(path, ctx.durationMs / 1000.0, options(ctx, seekrOnly))
                    AppLog.i(TAG, "start (seekrOnly=$seekrOnly) -> ${started.status}")
                    if (started.status == "disabled") return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w(TAG, "start failed: ${e.message}")
                    if (!seekrOnly) return
                }
                continue // poll straight away
            }
            delay(2_000)
        }
    }

    private fun options(ctx: Context, seekrOnly: Boolean) = ThumbnailStartOptions(
        seekrOnly = seekrOnly,
        imdbId = request.title.imdbId,
        tmdbId = request.title.tmdbId,
        mediaType = if (request.isEpisode) "episode" else "movie",
        season = request.episode?.season,
        episode = request.episode?.episode,
        hdr = ctx.hdr && !ctx.dolbyVision,
        dolbyVision = ctx.dolbyVision,
    )

    private fun apply(items: List<VideoThumbnailStatus.Item>) {
        val next = items.mapNotNull { item ->
            ApiClient.thumbnailImageUrl(item.url)?.let { Thumbnail((item.timeSec * 1000).toLong(), it) }
        }.sortedBy { it.timeMs }
        if (next != _thumbnails.value) _thumbnails.value = next
    }

    private companion object {
        const val TAG = "Thumbnails"
    }
}
