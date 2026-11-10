package com.strmr.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.Collections

/**
 * Drops a platform audio decoder that failed on real data, so the track goes to the FFmpeg
 * extension instead.
 *
 * Media3's decoder fallback only covers decoders that fail to start. Some phones' Dolby decoders
 * (the Pixel 7's `c2.dolby.eac3.decoder`) start fine on E-AC3 with Atmos (JOC) and then reject
 * every frame, which ends playback. Once a decoder has failed like that it is left out of the
 * list for the rest of the process, the renderer reports the format unsupported, and the next
 * prepare picks the FFmpeg audio renderer.
 */
@OptIn(UnstableApi::class)
object AudioDecoderFallback {
    private val failed: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** The default selector without the decoders that failed on this device. */
    val selector = MediaCodecSelector { mimeType, secure, tunneling ->
        MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling).filterNot { it.name in failed }
    }

    /**
     * The decoder behind [e] when it is an audio decoder that failed while decoding; it is
     * excluded from now on. Null for any other error, or a decoder already excluded.
     */
    fun exclude(e: PlaybackException): String? {
        if (e !is ExoPlaybackException || e.type != ExoPlaybackException.TYPE_RENDERER) return null
        if (e.errorCode != PlaybackException.ERROR_CODE_DECODING_FAILED) return null
        if (MimeTypes.getTrackType(e.rendererFormat?.sampleMimeType) != C.TRACK_TYPE_AUDIO) return null
        val name = (e.cause as? MediaCodecDecoderException)?.codecInfo?.name ?: return null
        return name.takeIf { failed.add(it) }
    }
}
