package com.strmr.tv.sports

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.LossyList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

suspend fun ApiClient.sportsStatus(): SportsStatus = send("GET", "/api/sports/status")

suspend fun ApiClient.sportsLeagues(): List<SportsLeague> =
    LossyList.decode(json, SportsLeague.serializer(), send<JsonObject>("GET", "/api/sports/leagues")["leagues"])

/** The cached scoreboard, or one ESPN day (`YYYY-MM-DD`, US Eastern) when the server answers dated queries. */
suspend fun ApiClient.sportsScoreboard(date: String?): SportsScoreboard =
    readScoreboard(json, send<JsonObject>("GET", "/api/sports/scoreboard", date?.let { mapOf("date" to it) }.orEmpty(), timeoutMs = 60_000))

/** A scoreboard answer, read leniently: bad games and league entries are skipped. */
fun readScoreboard(json: Json, response: JsonObject): SportsScoreboard {
    val fields = SportsFields(response)
    return SportsScoreboard(
        games = LossyList.decode(json, SportsGame.serializer(), response["games"]),
        updatedAt = fields.filled("updatedAt"),
        stale = fields.bool("stale") ?: false,
        leagues = (response["leagues"] as? JsonArray)?.let { LossyList.decode(json, SportsLeagueFreshness.serializer(), it) }.orEmpty(),
    )
}

suspend fun ApiClient.sportsGame(id: String): SportsGame = send("GET", "/api/sports/game/${segment(id)}", timeoutMs = 60_000)

/**
 * Channels that look like they carry the game. [broad] searches every source rather than the
 * admin's default sources for the league (the Expo "Search more sources"). A race session
 * names its [kind] (`racing`) and its weekend ([parentId]), which the server resolves it in.
 */
suspend fun ApiClient.sportsStreams(id: String, broad: Boolean, kind: String? = null, parentId: String? = null): List<SportsStreamGroup> {
    val query = buildMap {
        put("profileId", profileId)
        if (broad) put("scope", "all")
        if (kind != null && parentId != null) {
            put("kind", kind)
            put("parentId", parentId)
        }
    }
    val response = send<JsonObject>("GET", "/api/sports/game/${segment(id)}/streams", query, timeoutMs = 90_000)
    return LossyList.decode(json, SportsStreamGroup.serializer(), response["groups"])
}

/** Null when the server has no per-profile sports preferences (404 from older servers). */
suspend fun ApiClient.sportsPreferences(): SportsPreferencesEnvelope? = try {
    send("GET", "$profilePath/sports/preferences")
} catch (e: ApiError) {
    if (e.statusCode == 404) null else throw e
}

/** The server checks every preferences field is present, defaults included. */
private val PreferencesJson = Json(from = ApiJson) { encodeDefaults = true }

/** Saves over the envelope's revision; a 409 means another device saved first. */
suspend fun ApiClient.saveSportsPreferences(envelope: SportsPreferencesEnvelope): SportsPreferencesEnvelope =
    send("PUT", "$profilePath/sports/preferences", body = PreferencesJson.encodeToJsonElement(envelope).jsonObject)
