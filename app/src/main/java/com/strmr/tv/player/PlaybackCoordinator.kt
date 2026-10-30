package com.strmr.tv.player

import android.content.Intent
import com.strmr.tv.adaptive.AdaptiveThroughput
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.PrequeueStatus
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.TrackSelection
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.api.resolveSource
import com.strmr.tv.api.streamUrl
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveHlsPreference
import com.strmr.tv.live.LiveStream
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingRules
import com.strmr.tv.live.recordingStreamUrl
import com.strmr.tv.live.startRecordingHls
import com.strmr.tv.live.startLive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves a source through the backend prequeue (or a hand-picked source through resolve),
 * then opens [PlayerActivity] on the stream. Main thread only.
 */
object PlaybackCoordinator {
    /** The request being resolved; drives the stream-start screen until the player is up. */
    private val _resolving = MutableStateFlow<PlaybackRequest?>(null)
    val resolving: StateFlow<PlaybackRequest?> = _resolving
    val stage = MutableStateFlow("Starting…")
    /** The release being tried, when the server names one. */
    val detail = MutableStateFlow<String?>(null)
    val errorMessage = MutableStateFlow<String?>(null)
    /** Bumped whenever a playback session ends, so screens can refresh progress. */
    val sessionsEnded = MutableStateFlow(0)
    /** The player is on screen (log uploads hold off). */
    @Volatile var playerOpen = false

    /** The session the player picks up when it starts. */
    @Volatile var pending: PlayerSession? = null
        private set

    private val scope = MainScope()
    private var resolveJob: Job? = null
    private val prequeue = PrequeueResolver(scope) { s, d -> stage.value = s; detail.value = d }
    /** The details page's prequeue as it resolves, for its Audio & Subtitles button. */
    val warm: StateFlow<WarmPrequeue?> get() = prequeue.warm

    /**
     * Plays through the prequeue. [picks], made on details for the stream its prequeue resolved,
     * replace the server's choice when Play resolves that same stream. A movie or episode with a
     * library copy plays that instead ([playLibrary]).
     */
    fun play(request: PlaybackRequest, picks: PrequeuePick? = null) {
        request.preferredLibraryVersion?.let { return playLibrary(request, it) }
        playThroughPrequeue(request, picks)
    }

    private fun playThroughPrequeue(request: PlaybackRequest, picks: PrequeuePick?) = begin(request, "Searching…", resolve = {
        val status = prequeue.resolve(request)
        val subtitles = status.subtitleTracks.orEmpty()
        val source = sourceHints(status)
        when {
            picks == null -> ResolvedStream(status.streamPath!!, status.trackSelection, status.displayName, subtitles, source = source)
            !picks.appliesTo(status) -> {
                AppLog.i("ManualTracks", "details picks dropped: resolved a different stream")
                ResolvedStream(status.streamPath!!, status.trackSelection, status.displayName, subtitles, source = source)
            }
            else -> {
                val m = picks.tracks
                val applied = ManualTracks.applied(status.trackSelection, m.choice, m.audio, m.subtitles)
                AppLog.i("ManualTracks", "play prequeue with ${m.choice}: audio=${applied.audio?.streamIndex} " +
                    "subtitle=${applied.subtitle?.streamIndex} mode=${applied.subtitleMode} (server audio=" +
                    "${status.trackSelection?.audio?.streamIndex} subtitle=${status.trackSelection?.subtitle?.streamIndex})")
                ResolvedStream(status.streamPath!!, applied, status.displayName, subtitles, manualTracks = true, source = source)
            }
        }
    })

    /**
     * Plays a source picked by hand, bypassing the automatic prequeue. [manual] carries the
     * tracks chosen in its track panel; they replace the server's choice for those kinds.
     * [carried] (Switch Source) re-applies the languages that were playing; [resolved] skips the
     * resolve (Auto Switch resolved it already).
     */
    fun play(
        request: PlaybackRequest, source: SourceResult, manual: ManualSourceTracks? = null, carried: CarriedTracks? = null,
        resolved: Pair<String, TrackSelection?>? = null,
    ) =
        // A stream picked over a library copy (Switch Source) is no longer that copy.
        begin(request.copy(libraryVersion = null), "Preparing…", carried = carried, resolve = {
            val (path, tracks) = resolved ?: ApiClient.resolveSource(source, request.title.titleId, request.startPosition)
            val hints = SourceHints(source.serviceType.ifEmpty { null }, indexer = source.indexer.ifEmpty { null },
                                    attributes = source.attributes)
            if (manual == null || manual.choice.isEmpty) {
                ResolvedStream(path, tracks, source.title, source = hints)
            } else {
                val applied = ManualTracks.applied(tracks, manual.choice, manual.audio, manual.subtitles)
                AppLog.i("ManualTracks", "play with ${manual.choice}: audio=${applied.audio?.streamIndex} " +
                    "subtitle=${applied.subtitle?.streamIndex} mode=${applied.subtitleMode} (server audio=${tracks?.audio?.streamIndex} " +
                    "subtitle=${tracks?.subtitle?.streamIndex})")
                ResolvedStream(path, applied, source.title, manualTracks = true, source = hints)
            }
        })

    /** A trailer full screen, in the same player, without progress. */
    fun playTrailer(url: String, name: String) {
        if (_resolving.value != null) return
        pending = PlayerSession(PlaybackRequest(DetailsTarget("trailer", name, isMovie = true)), url, null, reportsProgress = false)
        launchPlayer()
    }

    /** A live channel: the server tunes it (a direct stream or an HLS session) and the player plays it. */
    fun playLive(channel: LiveChannel, programTitle: String?) {
        if (_resolving.value != null) return
        val request = PlaybackRequest(DetailsTarget(channel.id, programTitle ?: channel.name, isMovie = true, backdropUrl = null, logoUrl = channel.logoUrl))
        // A source remembered as needing HLS asks for it from the first tune (LiveHlsPreference).
        val live: suspend (Boolean) -> LiveStream = { force ->
            ApiClient.startLive(channel, programTitle, forceHls = force || LiveHlsPreference.prefersHls(channel))
        }
        begin(request, "Tuning…", live = live, liveChannel = channel,
              liveLabel = StreamInfo.live(channel.name))
    }

    /**
     * A Live TV recording, seekable, without progress (as Expo plays it): a finished one's
     * file, or one still recording over the server's growing HLS session from the start
     * ([RecordingHls]).
     */
    fun playRecording(recording: Recording) {
        if (_resolving.value != null) return
        val title = RecordingRules.title(recording)
        val request = PlaybackRequest(DetailsTarget(recording.id, title, isMovie = true))
        AppLog.i("Recordings", "play ${recording.id} status=${recording.status}")
        if (RecordingRules.isRecording(recording)) {
            begin(request, "Starting…", live = { _ -> ApiClient.startRecordingHls(recording, title) }, growing = true,
                  liveLabel = StreamInfo.recording(recording.channelName))
            return
        }
        pending = PlayerSession(request, ApiClient.recordingStreamUrl(recording, title), null, reportsProgress = false,
                                sourceLabel = StreamInfo.recording(recording.channelName))
        launchPlayer()
    }

    private fun launchPlayer() {
        AdaptiveThroughput.playbackStarting()
        App.context.startActivity(Intent(App.context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Stops a resolve in flight (Back on the stream-start screen). */
    fun cancelResolve() {
        resolveJob?.cancel()
        resolveJob = null
        _resolving.value = null
        detail.value = null
    }

    internal fun begin(
        request: PlaybackRequest,
        firstStage: String,
        live: (suspend (forceHls: Boolean) -> LiveStream)? = null,
        carried: CarriedTracks? = null,
        growing: Boolean = false,
        liveChannel: LiveChannel? = null,
        liveLabel: String? = null,
        resolve: suspend () -> ResolvedStream = { error("no resolver") },
    ) {
        if (_resolving.value != null) return
        AdaptiveThroughput.playbackStarting()
        _resolving.value = request
        stage.value = firstStage
        detail.value = null
        resolveJob = scope.launch {
            try {
                pending = if (live != null) {
                    val stream = live(false)
                    PlayerSession(request, stream.url, null, reportsProgress = false, liveSessionId = stream.sessionId,
                                  isLive = !growing, growingRestart = if (growing) suspend { live(false) } else null,
                                  retuneLive = live.takeIf { !growing }, liveChannel = liveChannel, sourceLabel = liveLabel)
                } else {
                    val settings = async { runCatching { ApiClient.playbackSettings() }.getOrNull() }
                    val r = resolve()
                    PlayerSession(request, ApiClient.streamUrl(r.path, request.streamMetadata(r.path) + StreamInfo.trackerQuery(r.path, r.source)), r.tracks, streamPath = r.path,
                                  releaseName = r.releaseName?.ifEmpty { null } ?: releaseFileName(r.path), subtitleStreams = r.subtitleStreams,
                                  playback = settings.await(), manualTracks = r.manualTracks, carried = carried,
                                  pickTracksOnDevice = r.pickTracksOnDevice,
                                  sourceLabel = StreamInfo.source(r.path, r.source, request.libraryVersion?.origin),
                                  sourceDolbyVision = r.source?.dolbyVisionProfile, sourceHdr10 = r.source?.hdr10 == true)
                }
                stage.value = "Starting…"
                detail.value = null
                launchPlayer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _resolving.value = null
                errorMessage.value = e.message ?: "Playback failed"
            }
        }
    }

    /**
     * The release's file name from a stream path ("…/Show.S01E02.1080p.WEB-GRP.mkv"), for
     * subtitle matching when the server names none; null for paths that aren't a video file.
     */
    private fun releaseFileName(path: String): String? {
        val name = runCatching { java.net.URLDecoder.decode(path.substringBefore('?').substringAfterLast('/'), "UTF-8") }.getOrNull()
        return name?.takeIf { Regex("\\.(mkv|mp4|m4v|avi|ts|mov|webm)$", RegexOption.IGNORE_CASE).containsMatchIn(it) }
    }

    /** What the prequeue says about the source it resolved: service type, provider, indexer. */
    private fun sourceHints(s: PrequeueStatus) = SourceHints(
        serviceType = s.serviceType?.ifEmpty { null } ?: s.selectedResult?.serviceType?.ifEmpty { null },
        debridProvider = s.debridProvider?.ifEmpty { null },
        indexer = s.selectedResult?.indexer?.ifEmpty { null },
        attributes = s.selectedResult?.attributes.orEmpty() + s.resultAttributes.orEmpty(),
        dolbyVisionProfile = s.dolbyVisionProfile?.ifEmpty { null },
        hdr10 = s.hasHdr10,
    )

    /** The player is on screen: the stream-start screen hands over to it. */
    fun playerStarted() {
        pending = null
        resolveJob = null
        _resolving.value = null
        detail.value = null
    }

    /**
     * The player closed. Pages reload watch state on this, so it waits (briefly) for the final
     * progress report to land; otherwise details would show the resume point from before.
     */
    fun playerClosed(saving: Job? = null) {
        scope.launch {
            withTimeoutOrNull(3_000) { saving?.join() }
            sessionsEnded.value += 1
        }
    }

    /** Starts resolving what `target`'s details page would play as it opens ([PrequeueResolver.prewarm]). */
    fun prewarm(target: DetailsTarget) = prequeue.prewarm(target)

    /** Starts resolving the next episode near the end of this one ([PrequeueResolver.prefetch]); [play] adopts it. */
    fun prefetch(request: PlaybackRequest) = prequeue.prefetch(request)
}
