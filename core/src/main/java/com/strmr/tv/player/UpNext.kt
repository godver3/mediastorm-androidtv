package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SkipSegment
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.library.ProfileDataContext
import com.strmr.tv.ui.settings.EpisodeAutoplayChanges
import com.strmr.tv.app.AppLog
import com.strmr.tv.ui.settings.CreditsDetectionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Skip Intro / Skip Recap / Next Episode, and moving on to the next episode, as the tvOS
 * player does: the next episode starts resolving at 90 % (or at the credits); at the credits
 * Next Episode replaces Skip Credits. Profile autoplay allows a short end countdown;
 * credits auto-skip additionally allows it to begin at the credits marker. Pausing holds the countdown; seeking out of the credits cancels it.
 * Back over the pill dismisses it for that segment. Driven by [PlayerController]'s clock.
 * With Credits Detection on and no credits from the server, the credits come from
 * [CreditsDetector] reading the video [surface].
 */
class UpNext(
    request: PlaybackRequest,
    streamPath: String?,
    private val scope: CoroutineScope,
    private val surface: () -> android.view.SurfaceView?,
    private val onSkip: (Long) -> Unit = {},
    private val onAdvance: (PlaybackRequest) -> Unit,
) {
    val nextEpisode: PlaybackRequest? = request.nextEpisode
    private val segments = SkipSegments(request, streamPath)
    private var detector: CreditsDetector? = null
    private var durationSec = 0.0
    private var cancelled = false
    private val _action = MutableStateFlow<SkipAction?>(null)
    val action: StateFlow<SkipAction?> = _action

    private var active: SkipSegment? = null
    private var dismissed: SkipSegment? = null
    private val context = ProfileDataContext.current
    private val policy = EpisodeAdvancePolicy()
    private val initialSettingsRevision = EpisodeAutoplayChanges.changes.value?.revision ?: 0L
    private var introAutoSkip = false
    private var recapAutoSkip = false
    private val skipped = mutableSetOf<SkipSegment>()
    private var prefetched = false
    private var advanced = false

    init {
        scope.launch {
            val before = EpisodeAutoplayChanges.changes.value
            val settings = ApiClient.playbackSettings()
            if (cancelled || context != ProfileDataContext.current) return@launch
            val latest = EpisodeAutoplayChanges.changes.value
            policy.autoplay = if (latest?.context == context && latest != before) latest.enabled else settings.nextEpisodeAutoPlay
            policy.skipCredits = settings.creditsAutoSkip
            introAutoSkip = settings.introAutoSkip
            recapAutoSkip = settings.recapAutoSkip
            // Reading credits on screen only feeds the next-episode countdown.
            if (nextEpisode != null && request.isEpisode && CreditsDetectionSettings.enabled(settings.creditsDetection) && !advanced && !cancelled) {
                detector = CreditsDetector { start -> segments.detectedCredits(start, durationSec) }
            }
        }
        scope.launch {
            EpisodeAutoplayChanges.changes.collect { change ->
                if (!cancelled && context == ProfileDataContext.current && change?.context == context && change.revision > initialSettingsRevision) policy.autoplay = change.enabled
            }
        }
    }

    /** Called about twice a second with the player's state. */
    fun tick(positionMs: Long, durationMs: Long, playing: Boolean) {
        if (advanced || cancelled || context != ProfileDataContext.current) return
        segments.refresh(scope, durationMs)
        durationSec = durationMs / 1000.0
        if (!segments.hasOutro) detector?.tick(positionMs / 1000.0, durationSec, surface())
        val found = segments.active(positionMs).takeIf { it != dismissed }
        val automatic = found?.let {
            ProfileSkipPolicy.enabled(it.type, introAutoSkip, recapAutoSkip)
        } == true
        if (playing && automatic && skipped.add(found)) {
            dismissed = found
            active = null
            _action.value = null
            onSkip((found.end * 1000).toLong())
            return
        }
        active = found
        policy.observePosition(positionMs, durationMs)
        if (nextEpisode != null && policy.tick(android.os.SystemClock.elapsedRealtime(), found?.type == "outro", playing)) {
            deliverAdvance(nextEpisode)
            return
        }
        prefetchIfNear(positionMs, durationMs)
        if (policy.dismissed) { _action.value = null; return }
        if (policy.mediaEnded && nextEpisode != null) {
            _action.value = SkipAction.NextEpisode(nextEpisode, policy.seconds, policy.paused)
            return
        }
        _action.value = active?.let { segment ->
            if (segment.type == "outro" && nextEpisode != null) SkipAction.NextEpisode(nextEpisode, policy.seconds, policy.paused)
            else SkipAction.Skip(segment)
        }
    }

    /** Back over the pill: it isn't offered again for this segment. */
    fun dismiss() {
        if (cancelled || context != ProfileDataContext.current) return
        if (active?.type == "outro" || policy.mediaEnded) policy.dismiss()
        dismissed = active
        active = null
        _action.value = null
    }

    fun toggleCountdownPause() { policy.togglePause() }

    /** Only a genuine media end offers the next episode; errors never advance. */
    fun ended(positionMs: Long, durationMs: Long, failed: Boolean): Boolean {
        if (nextEpisode == null || cancelled || context != ProfileDataContext.current) return false
        return policy.end(positionMs, durationMs, failed)
    }

    fun advance(next: PlaybackRequest) {
        if (context != ProfileDataContext.current || next != nextEpisode || !policy.claimAdvance()) return
        deliverAdvance(next)
    }

    private fun deliverAdvance(next: PlaybackRequest) {
        if (advanced || cancelled || context != ProfileDataContext.current) return
        advanced = true
        _action.value = null
        AppLog.i(TAG, "playing next: ${next.episode?.code}")
        onAdvance(next)
    }

    fun cancel() {
        cancelled = true
        policy.cancel()
        _action.value = null
        segments.cancel()
        detector?.close()
    }

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
        /** Share of the runtime after which the next episode starts resolving (as the Expo player). */
        const val PREFETCH_FRACTION = 0.9
    }
}
