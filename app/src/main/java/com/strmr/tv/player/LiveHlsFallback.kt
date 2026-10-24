package com.strmr.tv.player

/**
 * When a live channel's direct stream is opened again as HLS (Expo `useLiveDirectRecovery`,
 * tvOS `LivePlayerViewController+HLSFallback`). Some providers' streams play only through the
 * server's HLS session: a direct tune that fails before it plays falls back once, and every
 * tune after that asks for HLS. Once the fallback has played for a while it is confirmed, once,
 * and the source is remembered ([com.strmr.tv.live.LiveHlsPreference]).
 */
class LiveHlsFallback(direct: Boolean) {
    enum class Mode { AS_CONFIGURED, FALLBACK, CONFIRMED }

    var mode = Mode.AS_CONFIGURED
        private set
    private var direct = direct
    private var played = false

    /** Tunes ask the server for HLS (`format=hls`). */
    val forcesHls: Boolean get() = mode != Mode.AS_CONFIGURED

    /** A tune (the first or a retune) opened a direct stream or an HLS session. */
    fun tuned(direct: Boolean) {
        this.direct = direct
        played = false
    }

    fun playing() {
        played = true
    }

    /** The current tune failed or ended: true when it is the one to retry as HLS. */
    fun failed(): Boolean {
        if (mode != Mode.AS_CONFIGURED || !direct || played) return false
        mode = Mode.FALLBACK
        return true
    }

    /** The fallback is still playing a while on: true once, when the source is to be remembered. */
    fun confirm(): Boolean {
        if (mode != Mode.FALLBACK || !played) return false
        mode = Mode.CONFIRMED
        return true
    }
}
