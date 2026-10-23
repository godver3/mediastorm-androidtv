package com.strmr.tv.live

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@Serializable
private data class EpgStatus(val enabled: Boolean = false)

@Serializable
private data class LiveStart(val streamUrl: String? = null, val isDirect: Boolean? = null, val sessionId: String? = null, val playlistUrl: String? = null)

/** The profile's settings document, read raw so writes keep every field this app doesn't model. */
private suspend fun ApiClient.userSettingsObject(): JsonObject =
    json.parseToJsonElement(sendRaw("GET", "$profilePath/settings")) as? JsonObject ?: JsonObject(emptyMap())

/** The profile's `liveTV` favorites and hidden channels, from user settings. */
suspend fun ApiClient.liveChannelLists(): Pair<List<String>, List<String>> {
    val live = userSettingsObject()["liveTV"] as? JsonObject
    fun ids(key: String) = (live?.get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    return ids("favoriteChannels") to ids("hiddenChannels")
}

/**
 * Writes favorites / hidden channels. The settings PUT replaces the whole document, so it is
 * re-read and only these keys change (as the Expo app does).
 */
suspend fun ApiClient.saveLiveChannelLists(favorites: List<String>, hidden: List<String>) {
    val settings = userSettingsObject()
    val live = (settings["liveTV"] as? JsonObject ?: JsonObject(emptyMap())).toMutableMap()
    live["favoriteChannels"] = buildJsonArray { favorites.forEach { add(JsonPrimitive(it)) } }
    live["hiddenChannels"] = buildJsonArray { hidden.forEach { add(JsonPrimitive(it)) } }
    sendRaw("PUT", "$profilePath/settings", body = JsonObject(settings + ("liveTV" to JsonObject(live))))
}

/**
 * One page of channels. The server filters by source, category and search, and keeps
 * favorites (`favoriteIds`) first and through every filter.
 */
suspend fun ApiClient.liveChannels(
    favoriteIds: List<String>,
    favoritesOnly: Boolean,
    offset: Int,
    limit: Int,
    sourceId: String? = null,
    categories: List<String> = emptyList(),
    search: String = "",
): LiveChannelsPage {
    val body = buildJsonObject {
        put("profileId", profileId)
        put("offset", offset)
        put("limit", limit)
        put("favoritesOnly", favoritesOnly)
        putJsonArray("favoriteIds") { favoriteIds.forEach { add(JsonPrimitive(it)) } }
        sourceId?.let { put("sourceId", it) }
        if (categories.isNotEmpty()) putJsonArray("categories") { categories.forEach { add(JsonPrimitive(it)) } }
        if (search.isNotEmpty()) put("filter", search)
    }
    return send("POST", "/api/live/channels", body = body, timeoutMs = 60_000)
}

suspend fun ApiClient.epgEnabled(): Boolean =
    runCatching { send<EpgStatus>("GET", "/api/live/epg/status", mapOf("profileId" to profileId)).enabled }.getOrDefault(false)

/** Now / next for up to 50 EPG keys. */
suspend fun ApiClient.epgNow(keys: List<String>): List<EpgNowPlaying> = send(
    "POST", "/api/live/epg/now", mapOf("profileId" to profileId),
    body = buildJsonObject { putJsonArray("channels") { keys.forEach { add(JsonPrimitive(it)) } } },
)

/** Programmes overlapping `[now + startOffset, + hours]` for each key (every key answers, null when it has none). */
suspend fun ApiClient.epgSchedule(keys: List<String>, startOffsetMinutes: Int, hours: Int): Map<String, List<EpgProgram>> {
    val raw = json.parseToJsonElement(
        sendRaw(
            "GET", "/api/live/epg/schedule/batch",
            mapOf("channels" to keys.joinToString(","), "hours" to hours.toString(), "startOffset" to startOffsetMinutes.toString(),
                  "profileId" to profileId),
        ),
    ) as? JsonObject ?: return emptyMap()
    return raw.mapValues { (_, v: JsonElement) ->
        if (v is JsonNull) emptyList() else runCatching { json.decodeFromJsonElement<List<EpgProgram>>(v) }
            .onFailure { com.strmr.tv.app.AppLog.w("Live", "schedule decode: ${it.message}") }.getOrDefault(emptyList())
    }
}

/** Clears the server's channel cache. */
suspend fun ApiClient.clearLiveCache() {
    runCatching { sendRaw("POST", "/api/live/cache/clear") }
}

/**
 * Opens a channel on the server and returns the URL to play: a direct stream, or an HLS
 * session (kept alive with [keepLiveSessionAlive]). `token=` / `profileUnlock=` ride in the
 * query as on every header-less media URL.
 */
suspend fun ApiClient.startLive(channel: LiveChannel, programTitle: String?): LiveStream {
    val q = buildMap {
        put("url", channel.url)
        put("target", "native")
        put("profileId", profileId)
        put("mediaType", "channel")
        put("title", programTitle ?: channel.name)
        put("channelId", channel.playbackId ?: channel.id)
        put("channelName", channel.name)
        put("liveSourceUrl", channel.url)
        profileName?.takeIf { it.isNotEmpty() }?.let { put("profileName", it) }
        channel.sourceId?.let { put("sourceId", it); put("liveSourceId", it) }
        channel.tvgId?.let { put("itemId", it) }
        channel.tvgName?.let { put("tvgName", it) }
        channel.group?.let { put("category", it) }
        channel.logo?.let { put("liveChannelLogo", it) }
    }
    val start = send<LiveStart>("GET", "/api/live/hls/start", q, timeoutMs = 60_000)
    val direct = start.isDirect == true
    val path = (if (direct) start.streamUrl else start.playlistUrl)?.ifEmpty { null }
        ?: throw ApiError.NotReady("The server did not return a stream for this channel")
    return LiveStream(mediaUrl(path), if (direct) null else start.sessionId)
}

@Serializable
private data class KeepAlive(val growing: Boolean = false)

/**
 * Keeps a server HLS session open. With the position (a recording's growing session),
 * returns whether the server still reports the source as growing; null when it couldn't say.
 */
suspend fun ApiClient.keepLiveSessionAlive(
    sessionId: String, positionSeconds: Double? = null, paused: Boolean? = null, buffering: Boolean? = null,
): Boolean? = runCatching {
    val q = buildMap {
        positionSeconds?.let { put("time", "%.3f".format(java.util.Locale.US, it)) }
        paused?.let { put("paused", it.toString()) }
        buffering?.let { put("buffering", it.toString()) }
    }
    val text = sendRaw("POST", "/api/video/hls/${segment(sessionId)}/keepalive", q, promptOnLock = false, timeoutMs = 10_000)
    runCatching { json.decodeFromString<KeepAlive>(text).growing }.getOrNull()
}.getOrNull()

suspend fun ApiClient.stopLiveSession(sessionId: String) {
    runCatching { sendRaw("POST", "/api/video/hls/${segment(sessionId)}/stop", promptOnLock = false, timeoutMs = 10_000) }
}

/** A server-relative media path (`/live/stream?…`, `/video/hls/…`) under `/api`, with token and unlock added. */
internal fun ApiClient.mediaUrl(apiPath: String): String {
    val path = apiPath.substringBefore('?')
    val existing = apiPath.substringAfter('?', "").ifEmpty { null }
    val extra = listOfNotNull(
        token?.let { "token=${segment(it)}" },
        unlocks.token(profileId)?.let { "profileUnlock=${segment(it)}" },
    )
    return url("/api$path", encodedQuery = (listOfNotNull(existing) + extra).joinToString("&")).toString()
}
