package com.strmr.tv.live

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import kotlinx.serialization.Serializable

@Serializable
private data class HlsStart(val sessionId: String? = null, val playlistUrl: String? = null, val growing: Boolean? = null)

/** One recording from `/api/live/recordings` (the backend's `models.Recording`, the fields the app shows). */
@Serializable
data class Recording(
    val id: String,
    val status: String,
    val ruleId: String? = null,
    val channelId: String = "",
    val tvgId: String? = null,
    val channelName: String = "",
    val title: String = "",
    val description: String? = null,
    val startAt: String = "",
    val endAt: String = "",
    val outputPath: String? = null,
    val outputSizeBytes: Long? = null,
    val actualStartAt: String? = null,
    val actualEndAt: String? = null,
    val error: String? = null,
)

@Serializable
private data class RecordingsResponse(val recordings: List<Recording>? = null)

/** The profile's recordings, every status (Expo's TV Recordings page loads them all and filters locally). */
suspend fun ApiClient.recordings(): List<Recording> =
    send<RecordingsResponse>("GET", "/api/live/recordings", mapOf("profileId" to profileId)).recordings.orEmpty()

/** Cancels a scheduled recording, or stops one in progress (the video so far is kept). */
suspend fun ApiClient.cancelRecording(id: String) {
    sendRaw("POST", "/api/live/recordings/${segment(id)}/cancel")
}

/** Deletes a recording and its saved video. */
suspend fun ApiClient.deleteRecording(id: String) {
    sendRaw("DELETE", "/api/live/recordings/${segment(id)}")
}

/**
 * The recording's video (`/live/recordings/{id}/stream`, MPEG-TS), as the Expo player plays it:
 * the whole file of a recording that has finished (or stopped, or failed part way).
 * The query names the profile and title for the server's stream tracker, as Expo's does.
 */
fun ApiClient.recordingStreamUrl(recording: Recording, title: String): String {
    val q = buildMap {
        put("profileId", profileId)
        profileName?.takeIf { it.isNotEmpty() }?.let { put("profileName", it) }
        put("mediaType", "channel")
        put("title", title)
        (recording.tvgId?.ifEmpty { null } ?: recording.channelId.ifEmpty { null })?.let { put("itemId", it) }
    }
    val query = q.entries.joinToString("&") { "${segment(it.key)}=${segment(it.value)}" }
    return mediaUrl("/live/recordings/${segment(recording.id)}/stream?$query")
}

/**
 * A recording that is still recording, as a server HLS session from offset 0: an EVENT
 * playlist that grows until the recording stops (`/api/video/hls/start?path=recording:…`).
 * No `target`: video and AAC are copied into fMP4 segments.
 */
suspend fun ApiClient.startRecordingHls(recording: Recording, title: String): LiveStream {
    val file = recording.outputPath?.substringAfterLast('/')?.ifEmpty { null } ?: "recording.ts"
    val q = buildMap {
        put("path", "recording:${recording.id}/$file")
        put("profileId", profileId)
        profileName?.takeIf { it.isNotEmpty() }?.let { put("profileName", it) }
        put("mediaType", "channel")
        put("title", title)
    }
    val start = send<HlsStart>("GET", "/api/video/hls/start", q, timeoutMs = 60_000)
    val path = start.playlistUrl?.ifEmpty { null } ?: throw ApiError.NotReady("The server did not return a playlist for this recording")
    com.strmr.tv.app.AppLog.i("Recordings", "growing HLS session ${start.sessionId} (growing=${start.growing})")
    return LiveStream(mediaUrl(path), start.sessionId)
}
