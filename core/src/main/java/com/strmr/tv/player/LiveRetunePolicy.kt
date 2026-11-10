package com.strmr.tv.player

import androidx.media3.common.PlaybackException

/**
 * When a live channel whose source dropped is tuned again (the tvOS `LivePlayerViewController`
 * retune guard): at most [budget] retunes, each tune starting at least [spacingMs] after the
 * one before, and a channel that never played gives up at once (a tvOS load failure). The
 * budget belongs to one opening of the channel; opening it again starts a fresh one.
 *
 * Two differences, because ExoPlayer hands every drop straight to this policy, where the tvOS
 * engine first reopens the source in place (with backoff, and a budget that progress
 * restores) and its player only sees a source that stopped producing: a drop sooner than
 * [spacingMs] after its tune waits out the rest of the spacing instead of giving up (a
 * provider refuses an immediate reconnect), and a tune that played for [recoveredAfterMs]
 * earns a fresh budget, so a channel watched for hours survives more than three drops.
 *
 * Times are monotonic milliseconds.
 */
class LiveRetunePolicy(
    private val budget: Int = 3,
    private val spacingMs: Long = 20_000,
    private val recoveredAfterMs: Long = 60_000,
) {
    sealed interface Decision {
        /** Tune again after [delayMs]; [attempt] counts the retunes since the budget was last full. */
        data class Retune(val attempt: Int, val delayMs: Long) : Decision

        /** The channel never played this time: its own failure is what to show. */
        data object NeverPlayed : Decision

        /** The budget is spent. */
        data object Exhausted : Decision
    }

    private var retunes = 0
    private var lastTuneAt = 0L
    /** When the current tune first played; null until it does. */
    private var playingSince: Long? = null
    private var everPlayed = false

    /** A tune (the first or a retune) started. */
    fun tuned(now: Long) {
        lastTuneAt = now
        playingSince = null
    }

    /** The current tune is playing. */
    fun playing(now: Long) {
        if (playingSince == null) playingSince = now
        everPlayed = true
    }

    /** The source dropped: an end of stream, or an error [isSourceError] accepts. */
    fun sourceLost(now: Long): Decision {
        if (!everPlayed) return Decision.NeverPlayed
        if (playingSince?.let { now - it >= recoveredAfterMs } == true) retunes = 0
        if (retunes >= budget) return Decision.Exhausted
        retunes += 1
        playingSince = null
        return Decision.Retune(retunes, (lastTuneAt + spacingMs - now).coerceAtLeast(0))
    }

    companion object {
        /**
         * A failure of the source or the network on the way (worth a retune), not of decoding,
         * the device, or a stream this player can't read at all.
         */
        fun isSourceError(errorCode: Int): Boolean = errorCode in SOURCE_ERRORS

        private val SOURCE_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            // A cut-off or garbled stream or playlist.
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        )
    }
}
