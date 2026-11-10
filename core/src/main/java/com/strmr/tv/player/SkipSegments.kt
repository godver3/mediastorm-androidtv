package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SkipSegment
import com.strmr.tv.api.skipSegments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Intro / recap / credits ranges for the playing item from `POST /api/video/segments`, which
 * merges IntroDB, SkipDB and chapter names server-side. Posted once the duration is known
 * (ExoPlayer doesn't surface container chapters, so none are sent).
 */
class SkipSegments(private val request: PlaybackRequest) {
    private var segments: List<SkipSegment> = emptyList()
    private var postedDuration = 0L
    private var job: Job? = null

    fun refresh(scope: CoroutineScope, durationMs: Long) {
        if (durationMs <= 0 || durationMs / 1000 == postedDuration) return
        postedDuration = durationMs / 1000
        val body = buildJsonObject {
            put("duration", durationMs / 1000.0)
            putJsonArray("chapters") {}
            request.title.imdbId?.let { put("imdbId", it) }
            request.episode?.let {
                put("season", it.season)
                put("episode", it.episode)
            }
        }
        job?.cancel()
        job = scope.launch { runCatching { ApiClient.skipSegments(body) }.onSuccess { segments = it } }
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
        return segments.firstOrNull { t >= it.start && t < it.end - 1 && (it.type != "outro" || request.isEpisode) }
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
