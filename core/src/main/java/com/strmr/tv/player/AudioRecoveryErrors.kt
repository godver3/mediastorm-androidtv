package com.strmr.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException

/** Renderer context keeps video, network and container errors out of audio suggestions. */
@OptIn(UnstableApi::class)
fun AudioRecoveryPolicy.terminalFailure(error: PlaybackException, canPrepare: Boolean): AudioRecoveryHint? {
    val audio = error is ExoPlaybackException && error.type == ExoPlaybackException.TYPE_RENDERER &&
        MimeTypes.getTrackType(error.rendererFormat?.sampleMimeType) == C.TRACK_TYPE_AUDIO
    return terminalFailure(error.errorCode, audio, canPrepare)
}
