package com.strmr.tv.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** This endpoint replaces the document, so preserve subtitle and other existing overrides. */
fun contentAudioPreference(existing: JsonObject?, titleId: String, contentType: String, language: String): JsonObject = JsonObject(
    existing.orEmpty() + mapOf("contentId" to JsonPrimitive(titleId), "contentType" to JsonPrimitive(contentType), "audioLanguage" to JsonPrimitive(language)),
)

suspend fun ApiClient.saveContentAudioLanguage(titleId: String, contentType: String, language: String) {
    val profile = profileId
    val server = baseUrl
    val account = session?.accountId
    val existing = try {
        send<JsonObject>("GET", "$profilePath/preferences/content/${segment(titleId)}")
    } catch (e: ApiError.Http) {
        if (e.statusCode != 404) throw e
        null
    }
    check(profile == profileId && server == baseUrl && account == session?.accountId) { "Playback context changed" }
    sendRaw("PUT", "$profilePath/preferences/content", body = contentAudioPreference(existing, titleId, contentType, language))
}
