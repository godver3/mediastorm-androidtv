package com.strmr.mobile.cast

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.StartPoint
import com.strmr.tv.api.castStreamUrl
import com.strmr.tv.api.startCastHls
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.PlaybackRequest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/*
 * What each kind of receiver is handed for a title, as the Expo and iPhone apps build it.
 *
 * - Chromecast: always a re-encoded HLS session (`cast=true&forceAAC=true`, built from 0, the
 *   resume point sent as the LOAD's start time). The Default Media Receiver shows an MKV's video
 *   with no audio, and the cast profile forces a keyframe every 2 s, which the backend's
 *   fixed-duration playlist relies on.
 * - DLNA: the original file first (bit exact; an LG plays MKV with AC-3 straight), then a
 *   transcoded HLS session, then progressive H.264/AC-3 MPEG-TS (`dlnaProfile=avc-ts`) for old
 *   renderers, stopping at the first the renderer's Sink protocolInfo accepts.
 *
 * A receiver fetches from the server itself, with no headers: the session token rides in the
 * query, and the server must be at an address the TV can reach, which a remote-access (iroh)
 * loopback proxy is not.
 */

/** Throws when the app reaches the server through a loopback proxy (remote access). */
internal fun requireReachableServer() {
    val host = ApiClient.baseUrl.host.lowercase()
    if (host == "localhost" || host == "::1" || host == "[::1]" || host.startsWith("127.")) {
        throw CastException(
            "Casting needs the server's network address. Over remote access the TV can't reach the server; " +
                "connect on your home network first.",
        )
    }
}

/** Where to start: the position, else a resume given as a percentage once the length is known. */
internal fun castStartPosition(request: PlaybackRequest, position: Double, duration: Double?): Double {
    if (position > 0) return position
    val percent = request.start as? StartPoint.Percent ?: return 0.0
    return if (duration != null && duration > 0) duration * percent.value / 100 else 0.0
}

internal suspend fun chromecastMedia(
    request: PlaybackRequest, path: String, position: Double, audio: Int?, subtitle: Int?, subtitleLanguage: String?, poster: String?,
): Pair<CastMedia, String?> {
    requireReachableServer()
    val hls = ApiClient.startCastHls(path, request.streamMetadata(path), cast = true, audioTrack = audio, subtitleTrack = subtitle)
    val media = CastMedia(
        url = hls.playlistUrl, contentType = DlnaProtocol.HLS_MIME, title = request.title.name, subtitle = castSubtitle(request),
        posterUrl = poster, duration = hls.durationSeconds,
        startPosition = castStartPosition(request, position, hls.durationSeconds),
        subtitleLanguage = if (subtitle == null) null else subtitleLanguage,
    )
    return media to hls.sessionId
}

internal suspend fun dlnaMedia(
    transport: DlnaTransport, request: PlaybackRequest, path: String, position: Double, audio: Int?, subtitle: Int?, poster: String?,
): Pair<CastMedia, String?> {
    requireReachableServer()
    val sinks = transport.sinkProtocols()
    val metadata = request.streamMetadata(path)
    fun media(url: String, info: String, duration: Double?, seekable: Boolean = true) = CastMedia(
        url = url, contentType = DlnaProtocol.mime(info), title = request.displayTitle, subtitle = request.displaySubtitle,
        posterUrl = poster, duration = duration, startPosition = castStartPosition(request, position, duration),
        protocolInfo = info, seekable = seekable,
    )

    // 1. The original container.
    val direct = ApiClient.castStreamUrl(path, metadata)
    DlnaProtocol.mimesForPath(path).map { DlnaProtocol.protocolInfo(it, converted = false) }
        .firstOrNull { DlnaProtocol.accepts(sinks, it) }?.let { info ->
            AppLog.i(CastController.TAG, "DLNA: direct stream (${DlnaProtocol.mime(info)})")
            return media(direct, info, streamDuration(direct)) to null
        }
    // 2. A transcoded HLS session.
    val hlsInfo = DlnaProtocol.protocolInfo(DlnaProtocol.HLS_MIME, converted = true)
    if (DlnaProtocol.accepts(sinks, hlsInfo)) {
        val hls = ApiClient.startCastHls(path, metadata, cast = false, audioTrack = audio, subtitleTrack = subtitle)
        AppLog.i(CastController.TAG, "DLNA: transcoded HLS")
        return media(hls.playlistUrl, hlsInfo, hls.durationSeconds) to hls.sessionId
    }
    // 3. Progressive MPEG-TS, announced as `video/mpeg` with the `_ISO` profile (a Sony BRAVIA of
    //    2010 plays it only that way). A live mux: no byte offsets, so no Seek.
    val tsInfo = DlnaProtocol.protocolInfo("video/mpeg", profile = "AVC_TS_HD_24_AC3_ISO", operation = "00", converted = true)
    if (!DlnaProtocol.accepts(sinks, tsInfo)) throw CastException("${transport.device.name} doesn't accept any format the server can send.")
    AppLog.i(CastController.TAG, "DLNA: progressive MPEG-TS")
    val ts = ApiClient.castStreamUrl(path, metadata, mapOf("dlnaProfile" to "avc-ts"))
    return media(ts, tsInfo, streamDuration(direct), seekable = false) to null
}

/**
 * The length the server states on the stream (`Content-Duration`), for renderers that report
 * none: without it a finished episode can't be told from a stop.
 */
private suspend fun streamDuration(url: String): Double? = withContext(Dispatchers.IO) {
    runCatching {
        val client = ApiClient.http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
            (response.header("Content-Duration") ?: response.header("X-Content-Duration"))?.toDoubleOrNull()?.takeIf { it > 0 }
        }
    }.getOrNull()
}
