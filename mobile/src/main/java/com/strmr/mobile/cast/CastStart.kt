package com.strmr.mobile.cast

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.castMediaUrl
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.EpgProgram
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.stopLiveSession
import com.strmr.tv.player.PlaybackRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What a start has built for the receiver: its connection, the media to load, the server HLS
 * session behind it (kept alive, stopped with the cast) and, for a title, what watch progress is
 * reported against (null for a live channel).
 */
internal class PreparedCast(
    val transport: CastTransport,
    val media: CastMedia,
    val hlsSessionId: String?,
    val playback: Pair<PlaybackRequest, String>?,
)

/**
 * Casts a resolved stream ([path], as the local player would open it) to [device] from
 * [position] seconds. [audio] / [subtitle] are container stream indices (null: the server's pick /
 * none). Shows the remote at once; a failure is shown there.
 */
fun CastController.start(
    device: CastDevice, request: PlaybackRequest, path: String, position: Double,
    audio: Int?, subtitle: Int?, subtitleLanguage: String?,
) {
    val poster = ApiClient.castMediaUrl(request.title.posterUrl ?: request.episode?.imageUrl)
    val cast = ActiveCast(device, request.title.name, castSubtitle(request), poster)
    AppLog.i(CastController.TAG, "casting ${request.displayTitle} to ${device.name} (${device.kind}) from ${position.toInt()}s")
    begin(cast) {
        when (device.kind) {
            CastDevice.Kind.GOOGLE_CAST -> {
                val connected = GoogleCastService.connect(device)
                val (media, sessionId) = chromecastMedia(request, path, position, audio, subtitle, subtitleLanguage, poster)
                PreparedCast(connected, media, sessionId, request to path)
            }
            CastDevice.Kind.DLNA -> {
                val transport = dlnaTransport(device)
                val (media, sessionId) = dlnaMedia(transport, request, path, position, audio, subtitle, poster)
                PreparedCast(transport, media, sessionId, request to path)
            }
        }
    }
}

/**
 * Casts a live channel to [device] (the iPhone app's LivePlayer+Cast); [program] is what is on
 * now, shown with the channel's name and logo. No timeline, no progress.
 */
fun CastController.startLive(device: CastDevice, channel: LiveChannel, program: String?) {
    val logo = ApiClient.castMediaUrl(channel.logoUrl)
    val cast = ActiveCast(device, channel.name, program, logo, isLive = true, canSeek = false)
    AppLog.i(CastController.TAG, "casting channel ${channel.name} to ${device.name} (${device.kind})")
    begin(cast) {
        requireReachableServer()
        when (device.kind) {
            CastDevice.Kind.GOOGLE_CAST -> {
                val connected = GoogleCastService.connect(device)
                val opened = openLive(channel, program, forceHls = true)
                val type = if (opened.sessionId == null) directLiveContentType(channel) else DlnaProtocol.HLS_MIME
                PreparedCast(connected, liveMedia(opened.url, type, channel, program, logo), opened.sessionId, null)
            }
            CastDevice.Kind.DLNA -> {
                val transport = dlnaTransport(device)
                val (media, sessionId) = dlnaLiveMedia(transport, channel, program, logo)
                PreparedCast(transport, media, sessionId, null)
            }
        }
    }
}

/** "S01E02 · Pilot" under an episode's show name. */
internal fun castSubtitle(request: PlaybackRequest): String? =
    request.episode?.let { listOfNotNull(it.code, it.name?.ifEmpty { null }).joinToString(" · ") }

/**
 * Replaces what was casting, brings the remote up and builds and loads the media on the
 * receiver, a failure shown on the remote.
 */
internal fun CastController.begin(cast: ActiveCast, prepare: suspend () -> PreparedCast) {
    startJob?.cancel()
    startJob = scope.launch {
        finish("replaced by a new cast", stopReceiver = transport?.device?.id != cast.device.id)
        session = cast
        remoteVisible = true
        // A server session built for this start that the cast hasn't taken over yet.
        var orphan: String? = null
        try {
            val prepared = prepare()
            orphan = prepared.hlsSessionId
            ensureActive()
            hlsSessionId = prepared.hlsSessionId
            playback = prepared.playback
            transport = prepared.transport
            orphan = null
            prepared.transport.load(prepared.media)
            ensureActive()
            val media = prepared.media
            session = session?.copy(
                phase = ActiveCast.Phase.Active,
                canSeek = prepared.transport.canSeek && !media.isLive,
                state = CastPlaybackState(
                    phase = CastPlaybackState.Phase.LOADING, position = media.startPosition,
                    duration = if (media.isLive) 0.0 else media.duration ?: 0.0,
                ),
            )
            startPolling()
        } catch (e: CancellationException) {
            orphan?.let { withContext(NonCancellable) { ApiClient.stopLiveSession(it) } }
            throw e
        } catch (e: Exception) {
            AppLog.w(CastController.TAG, "cast to ${cast.device.name} failed: ${e.message}")
            orphan?.let { ApiClient.stopLiveSession(it) }
            transport?.stop()
            transport = null
            hlsSessionId?.let { ApiClient.stopLiveSession(it) }
            hlsSessionId = null
            playback = null
            session = session?.copy(phase = ActiveCast.Phase.Failed(e.message ?: "Casting failed."))
        }
    }
}
