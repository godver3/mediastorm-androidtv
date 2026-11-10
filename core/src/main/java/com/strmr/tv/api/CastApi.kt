package com.strmr.tv.api

import com.strmr.tv.live.mediaUrl
import kotlinx.serialization.Serializable

// What a Chromecast or DLNA TV is handed: URLs it can fetch with no headers of its own.

/** `GET /api/video/hls/start`: the session, its playlist (made absolute, with the token) and the length. */
data class CastHlsSession(val sessionId: String, val playlistUrl: String, val durationSeconds: Double?)

@Serializable
private data class HlsStartReply(val sessionId: String? = null, val playlistUrl: String? = null, val duration: Double? = null)

/**
 * Starts a server HLS session of the stream at [path] from 0 for a receiver. [cast] asks for the
 * cast profile (H.264 / AAC with a keyframe every 2 s, which the Default Media Receiver needs and
 * the server's fixed-length playlist relies on); without it the server transcodes as for any HLS
 * client (a DLNA TV that takes HLS). [audioTrack] / [subtitleTrack] are container stream indices;
 * a subtitle becomes a WebVTT rendition of the playlist.
 */
suspend fun ApiClient.startCastHls(
    path: String, metadata: Map<String, String>, cast: Boolean, audioTrack: Int?, subtitleTrack: Int?,
): CastHlsSession {
    val query = metadata.toMutableMap()
    query["path"] = path
    query["start"] = "0"
    query["profileId"] = profileId
    profileName?.takeIf { it.isNotEmpty() }?.let { query["profileName"] = it }
    if (cast) {
        query["cast"] = "true"
        query["forceAAC"] = "true"
    }
    audioTrack?.takeIf { it >= 0 }?.let { query["audioTrack"] = it.toString() }
    subtitleTrack?.takeIf { it >= 0 }?.let { query["subtitleTrack"] = it.toString() }
    val reply = send<HlsStartReply>("GET", "/api/video/hls/start", query, timeoutMs = 90_000)
    val playlist = reply.playlistUrl?.ifEmpty { null }
    val id = reply.sessionId?.ifEmpty { null }
    if (playlist == null || id == null) throw ApiError.NotReady("The server didn't start a stream for the TV.")
    return CastHlsSession(id, mediaUrl(playlist.removePrefix("/api")), reply.duration?.takeIf { it > 0 })
}

/**
 * The direct stream for a receiver: [streamUrl] with the session token in the query, since a
 * receiver sends no headers. [extra] adds query items (`dlnaProfile=avc-ts`).
 */
fun ApiClient.castStreamUrl(path: String, metadata: Map<String, String>, extra: Map<String, String> = emptyMap()): String {
    val base = streamUrl(path, metadata)
    val added = extra.toMutableMap()
    token?.let { added["token"] = it }
    if (added.isEmpty()) return base
    val query = added.entries.sortedBy { it.key }.joinToString("&") { "${segment(it.key)}=${segment(it.value)}" }
    return "$base&$query"
}

/**
 * Artwork or media for a receiver: an absolute URL as is, a server path (`/api/…` or `/…`) made
 * absolute with the token in its query; null for an empty one.
 */
fun ApiClient.castMediaUrl(pathOrUrl: String?): String? {
    val value = pathOrUrl?.trim().orEmpty()
    if (value.isEmpty()) return null
    if (value.startsWith("http")) return value
    return mediaUrl(if (value.startsWith("/api/")) value.removePrefix("/api") else value)
}
