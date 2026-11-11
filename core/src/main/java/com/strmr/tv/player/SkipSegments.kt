package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SkipSegment
import com.strmr.tv.api.VideoChapter
import com.strmr.tv.api.skipSegments
import com.strmr.tv.api.videoChapters
import com.strmr.tv.app.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Intro / recap / credits ranges for the playing item from `POST /api/video/segments`, which
 * merges IntroDB, SkipDB and chapter names server-side. Posted once the duration is known.
 * ExoPlayer doesn't surface container chapters, so when an episode comes back without credits
 * the file's chapters are read from the server's probe ([videoChapters]) and posted again, as
 * the Expo player posts the chapters mpv reports. On-device credits detection adds an outro
 * through [detectedCredits] when neither source has one.
 */
class SkipSegments(private val request: PlaybackRequest, private val streamPath: String?) {
    private var segments: List<SkipSegment> = emptyList()
    private var detected: SkipSegment? = null
    private var postedDuration = 0L
    private var job: Job? = null

    /** Whether the server's segments include credits (on-device detection then stays off). */
    val hasOutro: Boolean get() = segments.any { it.type == "outro" }

    fun refresh(scope: CoroutineScope, durationMs: Long) {
        if (durationMs <= 0 || durationMs / 1000 == postedDuration) return
        postedDuration = durationMs / 1000
        val duration = durationMs / 1000.0
        job?.cancel()
        job = scope.launch {
            runCatching { ApiClient.skipSegments(body(duration, emptyList())) }.onSuccess { segments = it }
            val path = streamPath?.takeIf { request.isEpisode && !hasOutro } ?: return@launch
            val chapters = runCatching { ApiClient.videoChapters(path) }.getOrNull().orEmpty()
            if (chapters.isEmpty()) return@launch
            runCatching { ApiClient.skipSegments(body(duration, chapters)) }.onSuccess {
                AppLog.i(TAG, "segments with ${chapters.size} chapters: ${it.joinToString { s -> s.type }}")
                segments = it
            }
        }
    }

    /** Credits found on screen at [startSec]: an outro to the end, unless the server has one. */
    fun detectedCredits(startSec: Double, durationSec: Double) {
        if (hasOutro || durationSec <= startSec) return
        detected = SkipSegment("outro", startSec, durationSec)
    }

    fun cancel() {
        job?.cancel()
    }

    /**
     * The segment playback is inside, when it merits a button. Movie end credits are left
     * alone (skipping them only ends the film, and may skip a post-credits scene).
     */
    fun active(positionMs: Long): SkipSegment? {
        val t = positionMs / 1000.0
        val all = if (hasOutro) segments else segments + listOfNotNull(detected)
        return all.firstOrNull { t >= it.start && t < it.end - 1 && (it.type != "outro" || request.isEpisode) }
    }

    private fun body(duration: Double, chapters: List<VideoChapter>) = buildJsonObject {
        put("duration", duration)
        putJsonArray("chapters") {
            chapters.forEach { c ->
                add(buildJsonObject {
                    put("title", c.title)
                    put("start", c.start)
                    if (c.end > c.start) put("end", c.end)
                })
            }
        }
        request.title.imdbId?.let { put("imdbId", it) }
        request.episode?.let {
            put("season", it.season)
            put("episode", it.episode)
        }
    }

    private companion object {
        const val TAG = "SkipSegments"
    }
}

/** The contextual pill over the transport: a seek past a segment, or the next episode at the credits. */
sealed interface SkipAction {
    val label: String

    data class Skip(val segment: SkipSegment) : SkipAction {
        override val label = when (segment.type) {
            "intro" -> "Skip Intro"
            "recap" -> "Skip Recap"
            "outro" -> "Skip Credits"
            else -> "Skip Preview"
        }
    }

    /** `countdown` is the seconds left when it will play by itself (credits auto-skip on). */
    data class NextEpisode(val next: PlaybackRequest, val countdown: Int?) : SkipAction {
        override val label = countdown?.let { "Next Episode in $it" } ?: "Next Episode"
    }
}
