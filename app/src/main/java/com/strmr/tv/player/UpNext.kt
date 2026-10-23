package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SkipSegment
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.app.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Skip Intro / Skip Recap / Next Episode, and moving on to the next episode, as the tvOS
 * player does: the next episode starts resolving at 90 % (or at the credits); at the credits
 * Next Episode replaces Skip Credits, and with the profile's credits auto-skip on it counts
 * down and plays by itself. Pausing holds the countdown; seeking out of the credits cancels it.
 * Back over the pill dismisses it for that segment. Driven by [PlayerController]'s clock.
 */
class UpNext(request: PlaybackRequest, private val scope: CoroutineScope, private val onAdvance: (PlaybackRequest) -> Unit) {
    val nextEpisode: PlaybackRequest? = request.nextEpisode
    private val segments = SkipSegments(request)
    private val _action = MutableStateFlow<SkipAction?>(null)
    val action: StateFlow<SkipAction?> = _action

    private var active: SkipSegment? = null
    private var dismissed: SkipSegment? = null
    private var autoSkip = false
    private var countdown: Int? = null
    private var countdownTickMs = 0L
    private var prefetched = false
    private var advanced = false

    init {
        if (nextEpisode != null) scope.launch { autoSkip = ApiClient.playbackSettings().creditsAutoSkip }
    }

    /** Called about twice a second with the player's state. */
    fun tick(positionMs: Long, durationMs: Long, playing: Boolean) {
        if (advanced) return
        segments.refresh(scope, durationMs)
        val found = segments.active(positionMs).takeIf { it != dismissed }
        if (found != active) {
            active = found
            countdown = if (found?.type == "outro" && nextEpisode != null && autoSkip) COUNTDOWN_SECONDS else null
            countdownTickMs = System.currentTimeMillis()
            if (countdown != null) AppLog.i(TAG, "credits: next episode in ${COUNTDOWN_SECONDS}s")
        }
        countdown?.let { left ->
            val now = System.currentTimeMillis()
            if (!playing) countdownTickMs = now
            else if (now - countdownTickMs >= 1_000) {
                countdownTickMs = now
                if (left <= 1) nextEpisode?.let { advance(it); return }
                countdown = left - 1
            }
        }
        prefetchIfNear(positionMs, durationMs)
        _action.value = active?.let { segment ->
            if (segment.type == "outro" && nextEpisode != null) SkipAction.NextEpisode(nextEpisode, countdown)
            else SkipAction.Skip(segment)
        }
    }

    /** Back over the pill: it isn't offered again for this segment. */
    fun dismiss() {
        val segment = active ?: return
        AppLog.i(TAG, "skip pill dismissed (${segment.type})")
        dismissed = segment
        active = null
        countdown = null
        _action.value = null
    }

    /** Playback reached its end: the next episode, when there is one, plays. */
    fun ended(): Boolean {
        val next = nextEpisode ?: return false
        advance(next)
        return true
    }

    fun advance(next: PlaybackRequest) {
        if (advanced) return
        advanced = true
        _action.value = null
        AppLog.i(TAG, "playing next: ${next.episode?.code}")
        onAdvance(next)
    }

    fun cancel() = segments.cancel()

    private fun prefetchIfNear(positionMs: Long, durationMs: Long) {
        val next = nextEpisode ?: return
        if (prefetched || durationMs <= 0) return
        if (positionMs < durationMs * PREFETCH_FRACTION && active?.type != "outro") return
        prefetched = true
        AppLog.i(TAG, "prefetching ${next.episode?.code} at ${positionMs / 1000}s of ${durationMs / 1000}s")
        PlaybackCoordinator.prefetch(next)
    }

    private companion object {
        const val TAG = "UpNext"
        const val COUNTDOWN_SECONDS = 7
        /** Share of the runtime after which the next episode starts resolving (as the Expo player). */
        const val PREFETCH_FRACTION = 0.9
    }
}
