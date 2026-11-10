package com.strmr.mobile.cast

import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStream
import com.strmr.tv.live.startLive
import com.strmr.tv.live.stopLiveSession

/*
 * A live channel on the TV, as the Expo and iPhone apps cast one: the server opens the channel
 * with `/api/live/hls/start?target=cast` (the same query the local tune sends, so the server's
 * stream limits, Stalker resolve and Stremio pick all apply) and the receiver reads it with no
 * timeline.
 *
 * - Chromecast: always an HLS session (`format=hls`). The cast target re-encodes to H.264 / AAC
 *   with segments kept until the receiver has fetched them, and the receiver is told it's LIVE.
 * - DLNA: the channel's own MPEG-TS mux when the source is set to direct (bit exact) and the
 *   renderer takes MPEG-TS, else that cast HLS session when the renderer takes HLS.
 */

/** `/api/live/hls/start` for a receiver; a direct stream has no session. */
internal suspend fun openLive(channel: LiveChannel, program: String?, forceHls: Boolean): LiveStream =
    ApiClient.startLive(channel, program, forceHls = forceHls, target = "cast")

internal fun liveMedia(url: String, contentType: String, channel: LiveChannel, program: String?, logo: String?, protocolInfo: String? = null) =
    CastMedia(
        url = url, contentType = contentType, title = channel.name, subtitle = program, posterUrl = logo, duration = null,
        startPosition = 0.0, protocolInfo = protocolInfo, seekable = false, isLive = true,
    )

/** A provider URL that is itself a playlist. */
internal fun isPlaylist(channel: LiveChannel): Boolean = channel.url.substringBefore('?').lowercase().endsWith(".m3u8")

/**
 * The upstream's own container, for a direct `/live/stream` proxy (the Expo app's
 * `castContentType`): a playlist when the provider serves one, else what the Default Media
 * Receiver accepts for a raw mux.
 */
internal fun directLiveContentType(channel: LiveChannel): String = if (isPlaylist(channel)) DlnaProtocol.HLS_MIME else "video/mp4"

/**
 * MPEG-TS as a renderer may list it, unprofiled first; the `_ISO` profile last for the renderers
 * that name one (the VOD transcode's, which a 2010 BRAVIA needs).
 */
private val transportStreamInfos: List<String>
    get() = listOf("video/vnd.dlna.mpeg-tts", "video/mpeg", "video/mp2t").map {
        DlnaProtocol.protocolInfo(it, operation = "00", converted = false)
    } + DlnaProtocol.protocolInfo("video/mpeg", profile = "AVC_TS_HD_24_AC3_ISO", operation = "00", converted = false)

internal suspend fun dlnaLiveMedia(
    transport: DlnaTransport, channel: LiveChannel, program: String?, logo: String?,
): Pair<CastMedia, String?> {
    val sinks = transport.sinkProtocols()
    val hlsInfo = DlnaProtocol.protocolInfo(DlnaProtocol.HLS_MIME, operation = "00", converted = true)
    val takesHls = DlnaProtocol.accepts(sinks, hlsInfo)
    val tsInfo = if (isPlaylist(channel)) null else transportStreamInfos.firstOrNull { DlnaProtocol.accepts(sinks, it) }
    fun media(url: String, info: String) = liveMedia(url, DlnaProtocol.mime(info), channel, program, logo, info)

    if (tsInfo != null) {
        // 1. The channel as the source is set to open it: direct is the provider's mux untouched.
        val opened = openLive(channel, program, forceHls = false)
        if (opened.sessionId == null) {
            AppLog.i(CastController.TAG, "DLNA live: direct MPEG-TS (${DlnaProtocol.mime(tsInfo)})")
            return media(opened.url, tsInfo) to null
        }
        if (takesHls) {
            AppLog.i(CastController.TAG, "DLNA live: HLS session")
            return media(opened.url, hlsInfo) to opened.sessionId
        }
        ApiClient.stopLiveSession(opened.sessionId!!)
    } else if (takesHls) {
        // 2. A cast HLS session.
        val opened = openLive(channel, program, forceHls = true)
        AppLog.i(CastController.TAG, "DLNA live: HLS session")
        return media(opened.url, hlsInfo) to opened.sessionId
    }
    throw CastException("${transport.device.name} can't play this channel's stream.")
}
