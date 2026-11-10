package com.strmr.tv.api

import com.strmr.tv.adaptive.ThroughputStore
import com.strmr.tv.app.Config
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Details, manual source selection and resolve.

/**
 * Details plus per-user state (episode `itemId` / `watched` / `resume`, up-next, resume) in one
 * call. [seasonType] asks for an episode ordering (the one just picked); without it the server
 * applies the profile's saved one.
 */
suspend fun ApiClient.detailsBundle(t: DetailsTarget, seasonType: String? = null): DetailsBundle {
    val q = buildMap {
        put("type", if (t.isMovie) "movie" else "series")
        seasonType?.takeIf { it.isNotEmpty() }?.let { put("seasonType", it) }
        put("titleId", t.titleId)
        put("name", t.name)
        t.tmdbId?.let { put("tmdbId", it) }
        t.imdbId?.let { put("imdbId", it) }
        t.tvdbId?.let { put("tvdbId", it) }
        t.year?.let { put("year", it.toString()) }
    }
    return send("GET", "$profilePath/details-bundle", q)
}

/**
 * Manual-selection results from the backend's indexers / scrapers, annotated with debrid
 * cache / usenet health (pending rows resolve via [sourceHealth]). Carries this route's
 * throughput estimate, as Expo's search does, so adaptive playback can cap the results.
 */
suspend fun ApiClient.searchSources(query: String, title: DetailsTarget, numbering: EpisodeNumbering? = null): List<SourceResult> {
    val q = buildMap {
        put("q", query)
        put("titleId", title.titleId)
        put("mediaType", if (title.isMovie) "movie" else "series")
        put("userId", profileId)
        put("includeSourceHealth", "true")
        put("limit", "50") // server default is 5
        title.imdbId?.let { put("imdbId", it) }
        title.year?.let { put("year", it.toString()) }
        // The episode's ordering, so "S01E02" is searched for as that ordering's episode.
        numbering?.let { n ->
            put("episodeNumberingId", n.seriesId)
            n.ordering?.takeIf { it.isNotEmpty() }?.let { put("episodeOrdering", it) }
        }
    }
    val list = json.parseToJsonElement(sendRaw("GET", "/api/indexers/search", q, headers = ThroughputStore.headers())) as? JsonArray ?: return emptyList()
    return list.mapIndexedNotNull { i, e -> (e as? JsonObject)?.let { SourceResult.from(it, i) } }
}

/**
 * Saves the profile's episode ordering for a series, which `/details-bundle` then applies. The
 * official ordering clears the choice. A non-official one turns off Trakt / Simkl scrobbling for
 * the series on the server, as in the Expo app.
 */
suspend fun ApiClient.setSeriesOrdering(tvdbId: Long, seasonType: String) {
    sendRaw("PUT", "$profilePath/series-ordering/$tvdbId", body = buildJsonObject { put("seasonType", seasonType) })
}

/** Long-polls the pending source-health checks of one search. */
suspend fun ApiClient.sourceHealth(token: String): SourceHealthSnapshot =
    send("GET", "/api/indexers/source-health", mapOf("token" to token, "waitMs" to "20000"), timeoutMs = 30_000)

/**
 * Resolves a manually chosen result to a stream path, waiting on the backend's queue when
 * resolution is asynchronous. The server also returns its track choice for this profile and
 * title, so manual picks get the same preference as prequeue.
 */
suspend fun ApiClient.resolveSource(result: SourceResult, titleId: String, startOffset: Double?): Pair<String, TrackSelection?> {
    val body = buildJsonObject {
        put("result", result.raw)
        put("profileId", profileId)
        put("titleId", titleId)
        put("clientId", Config.clientId)
        put("includeTrackSelection", true)
        startOffset?.let { put("startOffset", it) }
    }
    var resolution = json.decodeFromString<PlaybackResolution>(sendRaw("POST", "/api/playback/resolve", body = body, timeoutMs = 90_000))
    val deadline = System.currentTimeMillis() + 180_000
    while (resolution.webdavPath.isNullOrEmpty()) {
        val queueId = resolution.queueId
        if (queueId == null || queueId <= 0 || System.currentTimeMillis() > deadline) {
            throw ApiError.NotReady("Source could not be resolved")
        }
        delay(1_000)
        val q = mapOf("includeTrackSelection" to "true", "profileId" to profileId, "titleId" to titleId, "clientId" to Config.clientId)
        resolution = json.decodeFromString(sendRaw("GET", "/api/playback/queue/$queueId", q, timeoutMs = 60_000))
    }
    return resolution.webdavPath!! to resolution.trackSelection
}
