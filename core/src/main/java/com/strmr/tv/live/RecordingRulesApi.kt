package com.strmr.tv.live

import com.strmr.tv.api.ApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The profile's default padding around new recordings (`/api/live/recording-settings`). */
@Serializable
data class RecordingSettings(val paddingBeforeSeconds: Int = 0, val paddingAfterSeconds: Int = 0)

/** A rule recording every guide title matching [pattern] (a regex, ignoring case) on any channel. */
suspend fun ApiClient.createRegexRecordingRule(pattern: String) {
    sendRaw("POST", "/api/live/recording-rules", body = buildJsonObject {
        put("profileId", profileId)
        put("matchType", "regex")
        put("pattern", pattern)
        put("allChannels", true)
    })
}

/** Pauses / resumes a rule or changes its padding; only the values given change. */
suspend fun ApiClient.updateRecordingRule(id: String, enabled: Boolean? = null, paddingBefore: Int? = null, paddingAfter: Int? = null) {
    sendRaw("PUT", "/api/live/recording-rules/${segment(id)}", body = buildJsonObject {
        put("profileId", profileId)
        enabled?.let { put("enabled", it) }
        paddingBefore?.let { put("paddingBeforeSeconds", it) }
        paddingAfter?.let { put("paddingAfterSeconds", it) }
    })
}

suspend fun ApiClient.recordingSettings(): RecordingSettings =
    send("GET", "/api/live/recording-settings", mapOf("profileId" to profileId))

suspend fun ApiClient.saveRecordingSettings(settings: RecordingSettings) {
    sendRaw("PUT", "/api/live/recording-settings", body = buildJsonObject {
        put("profileId", profileId)
        put("paddingBeforeSeconds", settings.paddingBeforeSeconds)
        put("paddingAfterSeconds", settings.paddingAfterSeconds)
    })
}
