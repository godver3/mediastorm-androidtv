package com.strmr.tv.search

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ListedTitle
import com.strmr.tv.api.Title
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * `GET /api/search?q=&type=movie|series|person&userId=`, as the Expo TV search sends it. A
 * hit without a `mediaType` takes the request's type. The server drops people (and applies
 * rating limits) for kids profiles.
 */
suspend fun ApiClient.search(query: String, type: String): List<SearchResult> =
    // No hits may come back as null.
    send<List<JsonObject>?>("GET", "/api/search", mapOf("q" to query, "type" to type, "userId" to profileId)).orEmpty().map { hit ->
        val title = hit["title"]?.jsonObject
        val typed = if (title != null && title["mediaType"] == null) {
            JsonObject(hit + ("title" to JsonObject(title + ("mediaType" to JsonPrimitive(type)))))
        } else {
            hit
        }
        ApiClient.json.decodeFromJsonElement<SearchResult>(typed)
    }

@Serializable
private data class TopTenPage(val items: List<ListedTitle> = emptyList())

/** Top 10 Today, what the Search page shows before anything is typed. */
suspend fun ApiClient.searchTopTen(): List<Title> =
    // Can take a long time on a cold server cache.
    send<TopTenPage>("GET", "/api/discover/top-ten", mapOf("userId" to profileId), timeoutMs = 60_000L).items.map { it.title }
