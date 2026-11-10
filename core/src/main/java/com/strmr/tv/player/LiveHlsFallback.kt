package com.strmr.tv.player

/**
 * When a live channel's direct stream is opened again as HLS (Expo `useLiveDirectRecovery`,
 * tvOS `LivePlayerViewController+HLSFallback`). Some providers' streams play only through the
 * server's HLS session: a direct tune that fails before it plays falls back once, and every
 * tune after that asks for HLS. Once the fallback has played for a while with no stall it is
 * confirmed, once, and the source is remembered ([com.strmr.tv.live.LiveHlsPreference]).
 *
 * Once a direct stream of the source has played on this device ([directWorked]) it never falls
 * back: direct is known to work there, so a later failure is the provider's, which HLS can't fix.
 */
class LiveHlsFallback(direct: Boolean) {
    enum class Mode { AS_CONFIGURED, FALLBACK, CONFIRMED }

    var mode = Mode.AS_CONFIGURED
        private set
    /** A direct stream of this channel's source has played on this device. */
    var directWorked = false
    private var direct = direct
    private var played = false
    private var stalled = false

    /** Tunes ask the server for HLS (`format=hls`). */
    val forcesHls: Boolean get() = mode != Mode.AS_CONFIGURED

    /** A tune (the first or a retune) opened a direct stream or an HLS session. */
    fun tuned(direct: Boolean) {
        this.direct = direct
        played = false
        stalled = false
    }

    /** The current tune is playing; true once when it is the source's first direct stream to play. */
    fun playing(): Boolean {
        played = true
        if (!direct || mode != Mode.AS_CONFIGURED || directWorked) return false
        directWorked = true
        return true
    }

    /** The fallback stalled after it started: it isn't working well enough to remember. */
    fun stalled() {
        if (mode == Mode.FALLBACK && played) stalled = true
    }

    /** The current tune failed or ended: true when it is the one to retry as HLS. */
    fun failed(): Boolean {
        if (mode != Mode.AS_CONFIGURED || !direct || played || directWorked) return false
        mode = Mode.FALLBACK
        return true
    }

    /** The fallback is still playing with no stall: true once, when the source is to be remembered. */
    fun confirm(): Boolean {
        if (mode != Mode.FALLBACK || !played || stalled) return false
        mode = Mode.CONFIRMED
        return true
    }
}
