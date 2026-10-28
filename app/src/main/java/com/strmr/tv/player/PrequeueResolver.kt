package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.PrequeueStatus
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.api.prequeueStatus
import com.strmr.tv.api.startPrequeue
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [PlaybackCoordinator]'s backend prequeue: the warm-ups started as a details page opens or
 * near the end of an episode, and resolving a request through one (adopting a warm-up for the
 * same title and profile) until its stream is ready. [progress] gets the stream-start
 * screen's stage line and the release being tried. Main thread only.
 */
internal class PrequeueResolver(
    private val scope: CoroutineScope,
    private val progress: (stage: String, detail: String?) -> Unit,
) {
    private val _warm = MutableStateFlow<WarmPrequeue?>(null)
    /** The details page's prequeue as it resolves, for its Audio & Subtitles button. */
    val warm: StateFlow<WarmPrequeue?> = _warm

    /**
     * A prequeue started as a details page opened, for whatever the server would play there
     * (the movie, or the series' next-up episode). Adopted once; the title, profile and start
     * time stay behind so re-opening the page doesn't search again.
     */
    private var warmed: Warmed? = null

    private class Warmed(val titleId: String, val profile: String, val started: Long, var prequeue: Deferred<String?>?)

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
        val body = body(PlaybackRequest(target))
        _warm.value = WarmPrequeue(target.titleId, profile, WarmPrequeue.Phase.RESOLVING)
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
        while (System.currentTimeMillis() < deadline && _warm.value?.let { it.titleId == titleId && it.profile == profile } == true) {
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
        val w = _warm.value ?: return
        if (w.titleId == titleId && w.profile == profile && w.phase == WarmPrequeue.Phase.RESOLVING) {
            _warm.value = w.copy(phase = phase, status = status)
        }
    }

    /**
     * Starts resolving the next episode near the end of this one, so Up Next starts quickly;
     * [resolve] adopts it like a details-page prewarm.
     */
    fun prefetch(request: PlaybackRequest) {
        if (request.preferredLibraryVersion != null) return // plays its library copy
        val profile = ApiClient.profileId
        val body = body(request)
        val task = scope.async {
            if (!ApiClient.playbackSettings().prequeueOnOpen) return@async null
            runCatching { ApiClient.startPrequeue(body).prequeueId }.getOrNull()
        }
        warmed = Warmed(request.title.titleId, profile, System.currentTimeMillis(), task)
    }

    /** The ready prequeue for `request`: a warm-up for it when one is usable, else a fresh one. */
    suspend fun resolve(request: PlaybackRequest): PrequeueStatus {
        val w = warmed
        val pendingPrequeue = w?.prequeue
        if (w != null && w.titleId == request.title.titleId && w.profile == ApiClient.profileId && pendingPrequeue != null) {
            w.prequeue = null
            pendingPrequeue.await()?.let { id ->
                try {
                    return await(id, request.episode)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A different episode than the one asked for, or expired / failed: start fresh.
                }
            }
        }
        return await(ApiClient.startPrequeue(body(request)).prequeueId)
    }

    private fun body(request: PlaybackRequest): JsonObject = buildJsonObject {
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
    private suspend fun await(id: String, episode: PlaybackRequest.EpisodeInfo? = null): PrequeueStatus {
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
                else -> describe(status).let { (s, d) -> progress(s, d) }
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
