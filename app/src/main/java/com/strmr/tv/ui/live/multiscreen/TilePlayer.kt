package com.strmr.tv.ui.live.multiscreen

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.mediaHeaders
import com.strmr.tv.app.AppLog
import kotlinx.coroutines.flow.MutableStateFlow

/** What a tile's player reports about its stream, for the overlay. */
data class TileStreamInfo(
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val videoMime: String? = null,
    val audioMime: String? = null,
) {
    /** "1080p · 50 fps · AAC", or empty until known. */
    val label: String
        get() = listOfNotNull(
            height.takeIf { it > 0 }?.let { "${it}p" },
            frameRate.takeIf { it > 0 }?.let { "%.0f fps".format(it) },
            audioMime?.let(::audioName),
        ).joinToString(" · ")
}

private fun audioName(mime: String): String = when (mime) {
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus"
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MPEG"
    MimeTypes.AUDIO_OPUS -> "Opus"
    else -> mime.substringAfter('/').uppercase()
}

/**
 * One multiscreen tile's player: a lighter [com.strmr.tv.player.PlayerController] for a
 * server HLS session the tile owns. The same data source and renderers, but it never takes
 * audio focus (the multiscreen screen holds it once, or each new tile would pause the
 * others), never switches the display mode, and a muted tile has its audio track turned off,
 * not just its volume. No decoder fallback: a tile the hardware can't decode fails instead of
 * dropping to a software decoder that can't keep up ([DecoderLimits] caps the screens).
 */
@OptIn(UnstableApi::class)
class TilePlayer(context: Context, url: String, private val onFailed: () -> Unit) {
    val player: ExoPlayer
    val info = MutableStateFlow(TileStreamInfo())
    val buffering = MutableStateFlow(true)
    private var audible: Boolean? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            buffering.value = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            // A live session doesn't end: its source dropped.
            if (state == Player.STATE_ENDED) {
                AppLog.w("Multiscreen", "tile stream ended")
                onFailed()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val video = tracks.selected(C.TRACK_TYPE_VIDEO)
            // A muted tile's audio is off, so its format comes from the first audio group.
            val audio = tracks.selected(C.TRACK_TYPE_AUDIO)
                ?: tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }?.getTrackFormat(0)
            info.value = info.value.copy(
                width = video?.width?.takeIf { it > 0 } ?: info.value.width,
                height = video?.height?.takeIf { it > 0 } ?: info.value.height,
                frameRate = video?.frameRate?.takeIf { it > 0 } ?: info.value.frameRate,
                videoMime = video?.sampleMimeType ?: info.value.videoMime,
                audioMime = audio?.sampleMimeType ?: info.value.audioMime,
            )
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0 && size.height > 0) info.value = info.value.copy(width = size.width, height = size.height)
        }

        override fun onPlayerError(e: PlaybackException) {
            // Behind the live window, or a source that paused long enough for the playlist to
            // look stuck (a provider hiccup the server's session rides out): rejoin the same
            // session at its live edge, a few times a minute, before showing the error.
            if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW || (e.errorCode in IO_ERRORS && rejoin())) {
                AppLog.w("Multiscreen", "tile rejoining the live edge after ${e.errorCodeName}")
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            AppLog.e("Multiscreen", "tile playback error ${e.errorCodeName}: ${e.message}")
            onFailed()
        }
    }

    init {
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(false)
        val dataSource = OkHttpDataSource.Factory(ApiClient.http).setDefaultRequestProperties(ApiClient.mediaHeaders)
        player = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                false,
            )
            .build()
        player.addListener(listener)
        if (com.strmr.tv.BuildConfig.DEBUG) player.addAnalyticsListener(androidx.media3.exoplayer.util.EventLogger("Tile"))
        player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
        setAudible(false)
        player.playWhenReady = true
        player.prepare()
    }

    private val rejoins = ArrayDeque<Long>()

    /** Room for another rejoin: at most [REJOINS] in the last minute. */
    private fun rejoin(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        while (rejoins.isNotEmpty() && now - rejoins.first() > 60_000) rejoins.removeFirst()
        if (rejoins.size >= REJOINS) return false
        rejoins.addLast(now)
        return true
    }

    /** Sound on (volume and audio track) for the screen being listened to, off for the rest. */
    fun setAudible(on: Boolean) {
        if (audible == on) return
        audible = on
        player.volume = if (on) 1f else 0f
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !on).build()
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}

private const val REJOINS = 3
/** Media3's network and source error codes (2xxx). */
private val IO_ERRORS = 2000 until 3000

private fun Tracks.selected(type: Int): Format? =
    groups.firstOrNull { it.type == type && it.isSelected }?.let { group ->
        (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
    }
