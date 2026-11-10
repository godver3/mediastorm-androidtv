package com.strmr.mobile.cast

import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.strmr.tv.player.TrackPreferences
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** A connected Chromecast, driven through the session's [RemoteMediaClient]. Main thread only. */
class GoogleCastTransport(override val device: CastDevice, val session: CastSession) : CastTransport {
    override val canSeek = true
    private var subtitleLanguage: String? = null
    private var appliedSubtitle = false

    private val client: RemoteMediaClient
        get() = session.remoteMediaClient ?: throw CastException("The Chromecast disconnected.")

    /**
     * A Chromecast lists a default subtitle rendition inactive and never fetches it until a
     * track is switched on explicitly (the Expo app's `subtitleLanguage`).
     */
    private val statusCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = applySubtitle()
    }

    override suspend fun load(media: CastMedia) {
        val type = when {
            media.isLive -> MediaMetadata.MEDIA_TYPE_GENERIC
            media.subtitle == null -> MediaMetadata.MEDIA_TYPE_MOVIE
            else -> MediaMetadata.MEDIA_TYPE_TV_SHOW
        }
        val metadata = MediaMetadata(type).apply {
            putString(MediaMetadata.KEY_TITLE, media.title)
            media.subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            // A channel logo is wide, a poster tall.
            media.posterUrl?.let { addImage(if (media.isLive) WebImage(Uri.parse(it), 512, 288) else WebImage(Uri.parse(it), 480, 720)) }
        }
        val info = MediaInfo.Builder(media.url)
            .setContentUrl(media.url)
            .setContentType(media.contentType)
            .setStreamType(if (media.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .apply { media.duration?.takeIf { !media.isLive && it > 0 }?.let { setStreamDuration((it * 1000).toLong()) } }
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            // A channel starts at the live edge, wherever that is.
            .apply { if (!media.isLive) setCurrentTime((media.startPosition * 1000).toLong()) }
            .build()
        subtitleLanguage = media.subtitleLanguage
        appliedSubtitle = false
        val client = client
        client.unregisterCallback(statusCallback)
        client.registerCallback(statusCallback)
        suspendCancellableCoroutine { cont ->
            client.load(request).setResultCallback { result ->
                if (!cont.isActive) return@setResultCallback
                if (result.status.isSuccess) {
                    cont.resume(Unit)
                } else {
                    val why = result.status.statusMessage ?: "error ${result.status.statusCode}"
                    cont.resumeWithException(CastException("The Chromecast couldn't play this stream ($why)."))
                }
            }
        }
    }

    override suspend fun play() {
        client.play()
    }

    override suspend fun pause() {
        client.pause()
    }

    override suspend fun seek(seconds: Double) {
        client.seek(
            MediaSeekOptions.Builder().setPosition((seconds * 1000).toLong()).setResumeState(MediaSeekOptions.RESUME_STATE_UNCHANGED).build(),
        )
    }

    override suspend fun setVolume(volume: Float) {
        session.volume = volume.coerceIn(0f, 1f).toDouble()
    }

    override suspend fun status(): CastPlaybackState? {
        val client = session.remoteMediaClient ?: return null
        val status = client.mediaStatus ?: return null
        val duration = client.mediaInfo?.streamDuration?.takeIf { it > 0 }?.div(1000.0) ?: 0.0
        val phase = when (status.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> CastPlaybackState.Phase.PLAYING
            MediaStatus.PLAYER_STATE_PAUSED -> CastPlaybackState.Phase.PAUSED
            MediaStatus.PLAYER_STATE_BUFFERING -> CastPlaybackState.Phase.BUFFERING
            MediaStatus.PLAYER_STATE_LOADING -> CastPlaybackState.Phase.LOADING
            MediaStatus.PLAYER_STATE_IDLE ->
                if (status.idleReason == MediaStatus.IDLE_REASON_FINISHED) CastPlaybackState.Phase.FINISHED else CastPlaybackState.Phase.IDLE
            else -> CastPlaybackState.Phase.LOADING
        }
        return CastPlaybackState(phase, client.approximateStreamPosition / 1000.0, duration, session.volume.toFloat())
    }

    override suspend fun stop() {
        runCatching {
            session.remoteMediaClient?.unregisterCallback(statusCallback)
            session.remoteMediaClient?.stop()
        }
        GoogleCastService.disconnect()
    }

    private fun applySubtitle() {
        val language = subtitleLanguage ?: return
        if (appliedSubtitle) return
        val client = session.remoteMediaClient ?: return
        val tracks = client.mediaInfo?.mediaTracks?.takeIf { it.isNotEmpty() } ?: return
        appliedSubtitle = true
        val text = tracks.filter { it.type == MediaTrack.TYPE_TEXT }
        val track = text.firstOrNull { TrackPreferences.sameLanguage(it.language, language) }
            ?: text.firstOrNull { it.name?.contains(language, ignoreCase = true) == true }
            ?: return
        client.setActiveMediaTracks(longArrayOf(track.id))
    }
}
