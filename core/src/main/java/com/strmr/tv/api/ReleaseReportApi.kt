package com.strmr.tv.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The server's verified selection and effective language, not a source supplied by the player. */
@Serializable
data class ReleaseReportOptions(
    val sourceVersion: String,
    val releaseName: String = "Selected release",
    val audioLanguage: String = "",
    val audioTracks: List<ProbedAudioTrack> = emptyList(),
    val selectedAudioTrack: Int = -1,
    val canShare: Boolean = false,
    val hasEpisode: Boolean = false,
)

@Serializable
data class ReleaseReportReply(
    val report: SavedReleaseReport? = null,
    val replacement: PrequeueStartResponse? = null,
    val resumePosition: Double = 0.0,
)

@Serializable
data class SavedReleaseReport(val id: String)

suspend fun ApiClient.releaseReportOptions(id: String): ReleaseReportOptions =
    send("GET", "/api/playback/prequeue/${segment(id)}/report")

suspend fun ApiClient.submitReleaseAction(id: String, reporting: Boolean, body: JsonObject): ReleaseReportReply =
    send("POST", "/api/playback/prequeue/${segment(id)}/${if (reporting) "report" else "retry"}", body = body, timeoutMs = 10_000)

suspend fun ApiClient.cancelReleaseAlternative(id: String) {
    sendRaw("DELETE", "/api/playback/prequeue/${segment(id)}/retry", promptOnLock = false, timeoutMs = 5_000)
}

suspend fun ApiClient.undoReleaseReport(id: String) {
    sendRaw("DELETE", "/api/playback/bad-streams/${segment(id)}", mapOf("profileId" to profileId))
}
