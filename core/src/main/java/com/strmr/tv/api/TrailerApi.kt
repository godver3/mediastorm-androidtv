package com.strmr.tv.api

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
private data class TrailerStatus(val id: String, val status: String, val error: String? = null)

/**
 * A playable URL for `trailer`, as the Expo TV app gets one: a YouTube trailer is downloaded
 * by the server (`POST /api/metadata/trailers/prequeue`, polled every second until ready)
 * and served as an H.264 / AAC MP4 with range support; any other URL is played as it is.
 * Null when the download fails or takes over two minutes.
 *
 * A downloaded file the client never fetches is deleted about a minute after it is ready, so
 * call this again right before a late play rather than keeping the URL: the server returns the
 * same ID, and re-downloads only what it already cleaned up.
 */
suspend fun ApiClient.trailerUrl(trailer: Trailer): String? {
    val source = trailer.url.toHttpUrlOrNull() ?: return null
    if (source.host.lowercase() !in youTubeHosts) return trailer.url
    return runCatching {
        val queued = send<TrailerStatus>(
            "POST", "/api/metadata/trailers/prequeue",
            body = buildJsonObject { put("url", trailer.url) }, promptOnLock = false, timeoutMs = 15_000,
        )
        repeat(120) {
            val status = send<TrailerStatus>(
                "GET", "/api/metadata/trailers/prequeue/status", mapOf("id" to queued.id),
                promptOnLock = false, timeoutMs = 10_000,
            )
            when (status.status) {
                "ready" -> return@runCatching trailerServeUrl(queued.id)
                "failed" -> return@runCatching null
                else -> delay(1_000)
            }
        }
        null
    }.getOrNull()
}

/** The downloaded file; auth rides in `token=`, which the server accepts on this route. */
private fun ApiClient.trailerServeUrl(id: String): String {
    val query = buildMap {
        put("id", id)
        token?.let { put("token", it) }
    }
    return url("/api/metadata/trailers/prequeue/serve", query).toString()
}

/** The hosts the server's prequeue accepts. */
private val youTubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtu.be")
