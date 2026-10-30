package com.strmr.tv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.StartPoint
import com.strmr.tv.api.mediaHeaders
import com.strmr.tv.api.reportProgress
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.dv7.Dv7Playback
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One playback: ExoPlayer on the raw stream (platform decoders first, the FFmpeg extension
 * for audio the box can't decode, passthrough where the sink supports it), the server's track
 * choice applied once the tracks are known, the resume point, and progress every 10 s.
 */
@OptIn(UnstableApi::class)
class PlayerController(context: Context, val session: PlayerSession, private val onVideoFormat: (Format) -> Unit) {
    val request = session.request
    val player: ExoPlayer
    val isPlaying = MutableStateFlow(false)
    val buffering = MutableStateFlow(true)
    val tracks = MutableStateFlow(Tracks.EMPTY)
    val error = MutableStateFlow<String?>(null)
    val ended = MutableStateFlow(false)
    /** Position and duration (0 until known), ticked by the clock for the chrome. */
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val speed = MutableStateFlow(1f)
    /** Set when playback moves on to the next episode; the activity closes and starts it. */
    val advanceTo = MutableStateFlow<PlaybackRequest?>(null)
    /** Set by Switch Source; the activity closes and resumes on the chosen source. */
    val switchTo = MutableStateFlow<SourceSwitchRequest?>(null)
    /** The cues on screen, drawn by [SubtitleOverlay] in the device's style. */
    val cues = MutableStateFlow<List<Cue>>(emptyList())
    /** The video's display aspect (0 until known), so subtitles sit on the picture. */
    val videoAspect = MutableStateFlow(0f)
    /** The selected video and audio tracks' formats (null until known), for the Info panel's stream details. */
    val videoFormat = MutableStateFlow<Format?>(null)
    val audioFormat = MutableStateFlow<Format?>(null)

    /**
     * The track preferences after this device's overrides (Settings › Playback). A library file
     * has no server choice to map: the profile's languages pick both kinds here.
     */
    val effectiveTracks = when {
        session.manualTracks -> EffectiveTracks.resolve(session.tracks, session.playback, null, null, null)
        session.pickTracksOnDevice -> EffectiveTracks.current(null, session.playback).copy(clientAudio = true, clientSubtitles = true)
        else -> EffectiveTracks.current(session.tracks, session.playback)
    }
    private val prefs = TrackPreferences(session.tracks, effectiveTracks)
    private var prefsApplied = false
    private var startApplied = false
    private var audioRetried = false
    /** The playback's main-thread scope, cancelled shortly after [release]. */
    val scope = MainScope()
    private var progressJob: Job? = null
    /** The latest progress report in flight, which pages reloading after playback wait on. */
    private var lastReport: Job? = null
    private val progressLog = ProgressLog(request.title.name + (request.episode?.let { " · ${it.code}" } ?: ""))
    /** A downloaded or translated subtitle, drawn in place of ExoPlayer's cues while selected. */
    val external = ExternalSubtitles(scope, { player.currentPosition }) { cues.value = it }
    val online = OnlineSubtitles(this, scope)
    private val autoSwitch = AutoSwitch(this)

    /** Seek previews, for a resolved stream (not trailers or live). */
    val thumbnails: PlaybackThumbnails? =
        session.streamPath?.takeIf { !session.isLive && session.reportsProgress }?.let { PlaybackThumbnails(it, request) }
    /** A recording still recording, over its growing HLS session (set as the player is built). */
    private var recordingHls: RecordingHls? = null
    /** A live channel: its server session, and tuning it again when the source drops (set as the player is built). */
    var liveRetune: LiveRetune? = null
        private set
    val upNext: UpNext? = if (session.reportsProgress && !session.isLive) {
        UpNext(request, scope) { next ->
            report(ended = true)
            advanceTo.value = next
        }
    } else null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying.value = playing
            if (playing) {
                liveRetune?.playing()
                startProgress()
            } else if (player.playbackState == Player.STATE_READY) report(paused = true)
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering.value = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            if (state == Player.STATE_READY) applyPercentStart()
            if (state == Player.STATE_BUFFERING) liveRetune?.buffering()
            if (state == Player.STATE_ENDED && advanceTo.value == null) {
                if (upNext?.ended() == true) return
                recordingHls?.let { hls ->
                    hls.ended { ended.value = true }
                    return
                }
                liveRetune?.let { live ->
                    live.ended()
                    return
                }
                report(ended = true)
                ended.value = true
            }
        }

        override fun onTracksChanged(next: Tracks) {
            tracks.value = next
            if (!prefsApplied && !next.isEmpty) {
                prefsApplied = true
                player.trackSelectionParameters = prefs.apply(next, player.trackSelectionParameters)
                if (session.manualTracks) {
                    val audio = player.trackSelectionParameters.overrides.values.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
                    AppLog.i("ManualTracks", "player applied: audio=${audio?.mediaTrackGroup?.getFormat(audio.trackIndices.firstOrNull() ?: 0)?.id} " +
                        "subtitle=${prefs.subtitlePick?.format?.id} (${prefs.subtitlePick?.format?.language})")
                }
                if (effectiveTracks.clientAudio || effectiveTracks.clientSubtitles) {
                    AppLog.i("Player", "device track overrides: $effectiveTracks pick=${prefs.subtitlePick?.format?.language}")
                }
                session.carried?.let { carried ->
                    player.trackSelectionParameters = carried.apply(next, player.trackSelectionParameters)
                    AppLog.i("SourceSwitch", "carried tracks applied: $carried")
                }
                online.tracksReady(next, prefs.subtitlePick)
            }
            next.ofType(C.TRACK_TYPE_VIDEO).firstOrNull { it.selected }?.let {
                videoFormat.value = it.format
                onVideoFormat(it.format)
            }
            audioFormat.value = next.ofType(C.TRACK_TYPE_AUDIO).firstOrNull { it.selected }?.format
        }

        override fun onCues(cueGroup: CueGroup) {
            if (!external.selected.value) cues.value = cueGroup.cues
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0 && size.height > 0) videoAspect.value = size.width * size.pixelWidthHeightRatio / size.height
        }

        override fun onPlayerError(e: PlaybackException) {
            // A passthrough track the output refused: PassthroughGuard now offers PCM, so prepare again once.
            if (e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED && !audioRetried) {
                audioRetried = true
                AppLog.w("Player", "audio output failed (${e.message}); retrying with decoded audio")
                player.prepare()
                return
            }
            // A live stream that fell behind its window rejoins at the live edge.
            if (session.isLive && e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                AppLog.w("Player", "behind the live window; rejoining")
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            if (liveRetune?.failed(e) == true) {
                AppLog.w("Player", "live source error ${e.errorCodeName}: ${e.message}")
                return
            }
            AppLog.e("Player", "playback error ${e.errorCodeName}: ${e.message}", e)
            thumbnails?.cancel()
            error.value = e.message ?: e.errorCodeName
        }
    }

    init {
        // Passthrough only where the output takes it (not Bluetooth); PCM otherwise.
        val renderers = PassthroughGuard.renderersFactory(context)
            // Platform decoders first; the FFmpeg extension (TrueHD, DTS…) when none can.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val dataSource = OkHttpDataSource.Factory(ApiClient.http).setDefaultRequestProperties(ApiClient.mediaHeaders)
        player = ExoPlayer.Builder(context, renderers)
            // Dolby Vision Profile 7 rewritten for this box's decoders (player/dv7).
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, Dv7Playback.extractorsFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(listener)
        recordingHls = session.growingRestart?.let { RecordingHls(player, scope, session.liveSessionId, it) }
        liveRetune = session.retuneLive?.let { LiveRetune(player, scope, session.liveSessionId, session.liveChannel, it, ::liveGaveUp) }
        // Debug builds log every player event under the EventLogger tag.
        if (com.strmr.tv.BuildConfig.DEBUG) player.addAnalyticsListener(androidx.media3.exoplayer.util.EventLogger())
        // A growing recording's playlist plays from its start (0), not its live edge.
        recordingHls?.let { player.setMediaItem(it.mediaItem(session.url), 0) } ?: player.setMediaItem(MediaItem.fromUri(session.url))
        request.startPosition?.let { player.seekTo((it * 1000).toLong()) }
        player.playWhenReady = true
        player.prepare()
        autoSwitch.start()
        AppLog.i("Player", "play ${request.displayTitle} start=${request.start?.label ?: "0"}")
        thumbnails?.start(scope) {
            val video = tracks.value.ofType(C.TRACK_TYPE_VIDEO).firstOrNull { it.selected }?.format
            PlaybackThumbnails.Context(duration.value, player.currentPosition, buffering.value,
                                       hdr = video?.isHdr == true, dolbyVision = video?.isDolbyVision == true)
        }
    }

    /** Position for the chrome, skip segments and up next. */
    private val clock: Job = scope.launch {
        while (isActive) {
            position.value = player.currentPosition
            duration.value = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            upNext?.tick(position.value, duration.value, player.isPlaying)
            delay(250)
        }
    }

    fun seekTo(ms: Long) {
        if (session.isLive) return
        val end = duration.value.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo(ms.coerceIn(0, end))
        position.value = player.currentPosition
    }

    fun setSpeed(value: Float) {
        player.setPlaybackSpeed(value)
        speed.value = value
    }

    /** A percentage resume (Trakt import) once the duration is known. */
    private fun applyPercentStart() {
        if (startApplied) return
        startApplied = true
        val start = request.start as? StartPoint.Percent ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        player.seekTo((duration * start.value / 100).toLong())
    }

    /** A live channel that couldn't be kept on air closes, its message over the page beneath (as on tvOS). */
    private fun liveGaveUp(message: String) {
        PlaybackCoordinator.errorMessage.value = message
        ended.value = true
    }

    private fun startProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) report()
            }
        }
    }

    /** Position and duration in seconds, or null before the duration is known. */
    private fun times(): Pair<Double, Double>? {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return null
        return player.currentPosition / 1000.0 to duration / 1000.0
    }

    fun report(paused: Boolean = false, ended: Boolean = false, closing: Boolean = false) {
        if (!session.reportsProgress) return
        val (position, duration) = times() ?: return
        val body = request.progressReport(position, duration, paused, ended, sourcePath = session.streamPath, stream = streamHealth())
        val kind = when {
            ended -> ProgressLog.Kind.END
            closing -> ProgressLog.Kind.CLOSE
            paused -> ProgressLog.Kind.PAUSE
            else -> ProgressLog.Kind.PERIODIC
        }
        lastReport = scope.launch {
            withContext(NonCancellable) {
                try {
                    val reply = ApiClient.reportProgress(body)
                    progressLog.saved(kind, position, duration, reply)
                    if (reply.migrationRequested && !ended && !closing) {
                        autoSwitch.trigger("server: ${reply.migrationReason ?: "migration requested"}")
                    }
                } catch (e: Exception) {
                    progressLog.failed(kind, position, duration, e)
                }
            }
        }
    }

    private fun streamHealth() = StreamHealth(
        buffering = player.playbackState == Player.STATE_BUFFERING,
        bufferAheadSeconds = player.totalBufferedDuration / 1000.0,
        requiredMbps = player.videoFormat?.bitrate?.takeIf { it > 0 }?.let { it / 1_000_000.0 },
    )

    /** `byUser`: picked in a menu, which stops a running automatic subtitle search. */
    fun select(track: PlayerTrack, byUser: Boolean = true) {
        if (track.group.type == C.TRACK_TYPE_TEXT) {
            if (byUser) online.userChose()
            external.deselect()
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(track.group.type, false)
            .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.index))
            .build()
    }

    fun subtitlesOff() {
        online.userChose()
        external.deselect()
        disableEmbeddedSubtitles()
    }

    private fun disableEmbeddedSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        cues.value = emptyList()
    }

    /** The loaded external track, picked in the Subtitles menu. */
    fun selectExternal() {
        online.userChose()
        disableEmbeddedSubtitles()
        external.select()
    }

    /** A new external track (downloaded or translated), shown in place of the stream's subtitles. */
    fun showExternal(track: ExternalTrack) {
        disableEmbeddedSubtitles()
        external.show(track)
    }

    /**
     * Switch Source: the position and the playing audio / subtitle languages go with the
     * request; the activity closes this playback (saving the position) and starts the other.
     */
    fun switchSource(source: SourceResult) {
        val position = player.currentPosition
        val carried = CarriedTracks.capture(player.currentTracks, player.trackSelectionParameters, external.selected.value)
        AppLog.i("SourceSwitch", "switch from \"${session.releaseName}\" to \"${source.title}\" at ${position / 1000}s carried=$carried")
        switchTo.value = SourceSwitchRequest(SourceSwitchRules.resumeAt(request, position), source, carried)
    }

    /** Saves the position and lets go of the player; returns the last report, still finishing. */
    fun release(): Job? {
        if (!ended.value && advanceTo.value == null) report(paused = true, closing = true)
        clock.cancel()
        online.cancel()
        external.cancel()
        thumbnails?.cancel()
        upNext?.cancel()
        autoSwitch.cancel()
        progressJob?.cancel()
        // Each stops its server session.
        recordingHls?.release()
        liveRetune?.release()
        player.removeListener(listener)
        player.release()
        // Let the last report finish on its own.
        MainScope().launch { delay(5_000); scope.cancel() }
        return lastReport
    }
}

private val Format.isDolbyVision: Boolean
    get() = sampleMimeType == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION ||
        codecs?.let { it.startsWith("dvh") || it.startsWith("dva") } == true

private val Format.isHdr: Boolean
    get() = colorInfo?.colorTransfer.let { it == C.COLOR_TRANSFER_ST2084 || it == C.COLOR_TRANSFER_HLG }
