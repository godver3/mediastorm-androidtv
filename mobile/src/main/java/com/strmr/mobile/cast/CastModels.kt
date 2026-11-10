package com.strmr.mobile.cast

/**
 * A screen the phone can hand playback to: a Chromecast (Google Cast SDK) or a DLNA
 * MediaRenderer (SSDP + UPnP AVTransport).
 */
data class CastDevice(
    /** `cast:<route id>` or `dlna:<UDN>`, so the two lists never collide. */
    val id: String,
    val name: String,
    val model: String?,
    val kind: Kind,
    /** The device's LAN address, when known (shown under the name). */
    val address: String?,
) {
    enum class Kind(val label: String) { GOOGLE_CAST("Chromecast"), DLNA("DLNA") }

    /** "BRAVIA 4K · DLNA · 192.168.1.42", the model left out when it is just the name again. */
    val detail: String
        get() = listOfNotNull(model?.takeIf { it.isNotEmpty() && it != name }, kind.label, address).joinToString(" · ")
}

/** What a receiver reports about the media it is playing. */
data class CastPlaybackState(
    val phase: Phase = Phase.LOADING,
    /** Seconds. */
    val position: Double = 0.0,
    /** Seconds; 0 while the receiver doesn't know. */
    val duration: Double = 0.0,
    /** 0..1, null when the device doesn't say. */
    val volume: Float? = null,
) {
    enum class Phase {
        LOADING, PLAYING, PAUSED, BUFFERING, IDLE,
        /** The receiver reached the end of the media on its own. */
        FINISHED,
    }

    val isPlaying: Boolean get() = phase == Phase.PLAYING || phase == Phase.BUFFERING || phase == Phase.LOADING
}

/** The stream a receiver is told to fetch, with what it shows while it plays. */
data class CastMedia(
    val url: String,
    val contentType: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    /** Known up front (HLS session, `Content-Duration`), else null. */
    val duration: Double?,
    /** Where to start, in seconds of the media's own timeline. */
    val startPosition: Double,
    /** Subtitle rendition to switch on (a language), Chromecast only. */
    val subtitleLanguage: String? = null,
    /** DLNA: the `res@protocolInfo` the renderer accepted for this URL. */
    val protocolInfo: String? = null,
    /** DLNA's progressive MPEG-TS rung has no byte offsets, so no Seek. */
    val seekable: Boolean = true,
    /** A live channel: an endless stream with no timeline (no start time or duration sent). */
    val isLive: Boolean = false,
)

/**
 * One live connection to a receiver. Every call is a network round trip; all are called from
 * the main thread and do their I/O off it.
 */
interface CastTransport {
    val device: CastDevice
    /** Progressive transcodes (DLNA's MPEG-TS rung) cannot seek. */
    val canSeek: Boolean
    suspend fun load(media: CastMedia)
    suspend fun play()
    suspend fun pause()
    suspend fun seek(seconds: Double)
    suspend fun setVolume(volume: Float)
    /** Null when the receiver did not answer. */
    suspend fun status(): CastPlaybackState?
    /** Stops playback on the receiver and lets it go. */
    suspend fun stop()
}

/** A cast failure with a message fit for the remote. */
class CastException(message: String) : Exception(message)
