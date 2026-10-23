package com.strmr.tv.player

import android.content.Intent
import com.strmr.tv.adaptive.AdaptiveThroughput
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.PrequeueStatus
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.api.prequeueStatus
import com.strmr.tv.api.resolveSource
import com.strmr.tv.api.startPrequeue
import com.strmr.tv.api.streamUrl
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.Config
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStream
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingRules
import com.strmr.tv.live.recordingStreamUrl
import com.strmr.tv.live.startRecordingHls
import com.strmr.tv.live.startLive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    /** The details page's prequeue as it resolves, for its Audio & Subtitles button. */
    val warm = MutableStateFlow<WarmPrequeue?>(null)
    /** The player is on screen (log uploads hold off). */
    @Volatile var playerOpen = false

    /** The session the player picks up when it starts. */
    @Volatile var pending: PlayerSession? = null
        private set

    private val scope = MainScope()
    private var resolveJob: Job? = null
    /**
     * A prequeue started as a details page opened, for whatever the server would play there
     * (the movie, or the series' next-up episode). Adopted once; the title, profile and start
     * time stay behind so re-opening the page doesn't search again.
     */
    private var warmed: Warmed? = null

    private class Warmed(val titleId: String, val profile: String, val started: Long, var prequeue: Deferred<String?>?)

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
        val status = resolveStream(request)
        val subtitles = status.subtitleTracks.orEmpty()
        when {
            picks == null -> ResolvedStream(status.streamPath!!, status.trackSelection, status.displayName, subtitles)
            !picks.appliesTo(status) -> {
                AppLog.i("ManualTracks", "details picks dropped: resolved a different stream")
                ResolvedStream(status.streamPath!!, status.trackSelection, status.displayName, subtitles)
            }
            else -> {
                val m = picks.tracks
                val applied = ManualTracks.applied(status.trackSelection, m.choice, m.audio, m.subtitles)
                AppLog.i("ManualTracks", "play prequeue with ${m.choice}: audio=${applied.audio?.streamIndex} " +
                    "subtitle=${applied.subtitle?.streamIndex} mode=${applied.subtitleMode} (server audio=" +
                    "${status.trackSelection?.audio?.streamIndex} subtitle=${status.trackSelection?.subtitle?.streamIndex})")
                ResolvedStream(status.streamPath!!, applied, status.displayName, subtitles, manualTracks = true)
            }
        }
    })

    /**
     * Plays a source picked by hand, bypassing the automatic prequeue. [manual] carries the
     * tracks chosen in its track panel; they replace the server's choice for those kinds.
     * [carried] (Switch Source) re-applies the languages that were playing.
     */
    fun play(request: PlaybackRequest, source: SourceResult, manual: ManualSourceTracks? = null, carried: CarriedTracks? = null) =
        // A stream picked over a library copy (Switch Source) is no longer that copy.
        begin(request.copy(libraryVersion = null), "Preparing…", carried = carried, resolve = {
            val (path, tracks) = ApiClient.resolveSource(source, request.title.titleId, request.startPosition)
            if (manual == null || manual.choice.isEmpty) {
                ResolvedStream(path, tracks, source.title)
            } else {
                val applied = ManualTracks.applied(tracks, manual.choice, manual.audio, manual.subtitles)
                AppLog.i("ManualTracks", "play with ${manual.choice}: audio=${applied.audio?.streamIndex} " +
                    "subtitle=${applied.subtitle?.streamIndex} mode=${applied.subtitleMode} (server audio=${tracks?.audio?.streamIndex} " +
                    "subtitle=${tracks?.subtitle?.streamIndex})")
                ResolvedStream(path, applied, source.title, manualTracks = true)
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
        begin(request, "Tuning…", live = { ApiClient.startLive(channel, programTitle) })
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
            begin(request, "Starting…", live = { ApiClient.startRecordingHls(recording, title) }, growing = true)
            return
        }
        pending = PlayerSession(request, ApiClient.recordingStreamUrl(recording, title), null, reportsProgress = false)
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
        live: (suspend () -> LiveStream)? = null,
        carried: CarriedTracks? = null,
        growing: Boolean = false,
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
                    val stream = live()
                    PlayerSession(request, stream.url, null, reportsProgress = false, liveSessionId = stream.sessionId,
                                  isLive = !growing, growingRestart = live.takeIf { growing },
                                  retuneLive = live.takeIf { !growing })
                } else {
                    val settings = async { runCatching { ApiClient.playbackSettings() }.getOrNull() }
                    val r = resolve()
                    PlayerSession(request, ApiClient.streamUrl(r.path, request.streamMetadata(r.path)), r.tracks, streamPath = r.path,
                                  releaseName = r.releaseName?.ifEmpty { null } ?: releaseFileName(r.path), subtitleStreams = r.subtitleStreams,
                                  playback = settings.await(), manualTracks = r.manualTracks, carried = carried,
                                  pickTracksOnDevice = r.pickTracksOnDevice)
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

    /** The player is on screen: the stream-start screen hands over to it. */
    fun playerStarted() {
        pending = null
        resolveJob = null
        _resolving.value = null
        detail.value = null
    }

    fun playerClosed() {
        sessionsEnded.value += 1
    }

    /**
     * Starts resolving what `target`'s details page would play as soon as it opens, so Play
     * starts quickly, unless the profile turned prequeue off. At most once per title and
     * profile every ten minutes.
     */
    fun prewarm(target: DetailsTarget) {
        if (target.fromLibrary) return // Play uses the library's copy
        val profile = ApiClient.profileId
        warmed?.let { if (it.titleId == target.titleId && it.profile == profile && System.currentTimeMillis() - it.started < 600_000) return }
        // No episode or start point: the server targets the series' next-up episode.
        val body = prequeueBody(PlaybackRequest(target))
        warm.value = WarmPrequeue(target.titleId, profile, WarmPrequeue.Phase.RESOLVING)
        val task = scope.async {
            val id = if (runCatching { ApiClient.playbackSettings().prequeueOnOpen }.getOrDefault(false)) {
                runCatching { ApiClient.startPrequeue(body).prequeueId }.getOrNull()
            } else null
            if (id == null) settleWarm(target.titleId, profile, WarmPrequeue.Phase.UNAVAILABLE, null)
            else scope.launch { watchWarm(id, target.titleId, profile) }
            id
        }
        warmed = Warmed(target.titleId, profile, System.currentTimeMillis(), task)
    }

    /** Follows the details prequeue until it settles, for the page's Audio & Subtitles button. */
    private suspend fun watchWarm(id: String, titleId: String, profile: String) {
        val deadline = System.currentTimeMillis() + 180_000
        while (System.currentTimeMillis() < deadline && warm.value?.let { it.titleId == titleId && it.profile == profile } == true) {
            try {
                val status = ApiClient.prequeueStatus(id)
                if (status.status == "ready") {
                    AppLog.i("Prequeue", "details prequeue $id ready: ${status.audioTracks?.size ?: 0} audio, ${status.subtitleTracks?.size ?: 0} subtitles")
                    return settleWarm(titleId, profile, WarmPrequeue.Phase.READY, status)
                }
                if (status.status == "failed" || status.status == "expired") break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Prequeue", "details prequeue $id status failed: ${e.message}")
            }
            delay(1500)
        }
        AppLog.i("Prequeue", "details prequeue $id unavailable")
        settleWarm(titleId, profile, WarmPrequeue.Phase.UNAVAILABLE, null)
    }

    private fun settleWarm(titleId: String, profile: String, phase: WarmPrequeue.Phase, status: PrequeueStatus?) {
        val w = warm.value ?: return
        if (w.titleId == titleId && w.profile == profile && w.phase == WarmPrequeue.Phase.RESOLVING) {
            warm.value = w.copy(phase = phase, status = status)
        }
    }

    /**
     * Starts resolving the next episode near the end of this one, so Up Next starts quickly;
     * [play] adopts it like a details-page prewarm.
     */
    fun prefetch(request: PlaybackRequest) {
        if (request.preferredLibraryVersion != null) return // plays its library copy
        val profile = ApiClient.profileId
        val body = prequeueBody(request)
        val task = scope.async {
            if (!ApiClient.playbackSettings().prequeueOnOpen) return@async null
            runCatching { ApiClient.startPrequeue(body).prequeueId }.getOrNull()
        }
        warmed = Warmed(request.title.titleId, profile, System.currentTimeMillis(), task)
    }

    private suspend fun resolveStream(request: PlaybackRequest): PrequeueStatus {
        val w = warmed
        val pendingPrequeue = w?.prequeue
        if (w != null && w.titleId == request.title.titleId && w.profile == ApiClient.profileId && pendingPrequeue != null) {
            w.prequeue = null
            pendingPrequeue.await()?.let { id ->
                try {
                    return awaitPrequeue(id, request.episode)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A different episode than the one asked for, or expired / failed: start fresh.
                }
            }
        }
        return awaitPrequeue(ApiClient.startPrequeue(prequeueBody(request)).prequeueId)
    }

    private fun prequeueBody(request: PlaybackRequest): JsonObject = buildJsonObject {
        val t = request.title
        put("titleId", t.titleId)
        put("titleName", t.name)
        put("mediaType", if (t.isMovie) "movie" else "series")
        put("userId", ApiClient.profileId)
        put("clientId", Config.clientId)
        put("reason", "details")
        put("skipHLS", true)
        t.imdbId?.let { put("imdbId", it) }
        t.tmdbId?.let { put("tmdbId", it) }
        t.tvdbId?.let { put("tvdbId", it) }
        t.year?.let { put("year", it) }
        request.episode?.let { ep ->
            put("seasonNumber", ep.season)
            put("episodeNumber", ep.episode)
            ep.absolute?.takeIf { it > 0 }?.let { put("absoluteEpisodeNumber", it) }
        }
        request.startPosition?.let { put("startOffset", it) }
    }

    /**
     * Polls a prequeue until its stream is ready. With `episode`, gives up straight away if
     * the prequeue is resolving a different one.
     */
    private suspend fun awaitPrequeue(id: String, episode: PlaybackRequest.EpisodeInfo? = null): PrequeueStatus {
        val deadline = System.currentTimeMillis() + 180_000
        while (System.currentTimeMillis() < deadline) {
            val status = ApiClient.prequeueStatus(id)
            if (episode != null && (status.targetEpisode?.seasonNumber != episode.season ||
                    status.targetEpisode.episodeNumber != episode.episode)) {
                throw ApiError.NotReady("Prequeue is for a different episode")
            }
            when (status.status) {
                "ready" -> {
                    if (status.streamPath.isNullOrEmpty()) throw ApiError.NotReady("Stream resolved without a path")
                    return status
                }
                "failed", "expired" -> throw ApiError.NotReady(status.error ?: "No playable stream found")
                else -> describe(status).let { (s, d) -> stage.value = s; detail.value = d }
            }
            delay(750)
        }
        throw ApiError.NotReady("Timed out waiting for a stream")
    }

    /** A friendly line for the prequeue's stage, plus the release being tried when there is one. */
    private fun describe(s: PrequeueStatus): Pair<String, String?> {
        val d = s.progressDetail?.ifEmpty { null }
        return when (s.progressStage ?: s.status) {
            "queued", "metadata" -> "Starting…" to null
            "searching" -> "Searching…" to null
            "ranking", "preparing_candidates" -> "Ranking…" to null
            "resolving_candidate", "waiting_provider" -> "Fetching…" to d
            "validating_candidate" -> "Checking…" to d
            "analyzing_media", "probing", "selecting_tracks" -> "Inspecting…" to null
            "preparing_playback" -> "Preparing…" to null
            else -> "Searching…" to null
        }
    }
}
