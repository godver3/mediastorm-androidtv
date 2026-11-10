package com.strmr.mobile.downloads

import com.strmr.tv.adaptive.ThroughputStore
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.EpisodeNumbering
import com.strmr.tv.api.SourceResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The download picker's server calls, as the iPhone app's `APIClient+Downloads`.

/**
 * The indexer search ranked for downloading (`downloadRanking`, which favours smaller, widely
 * compatible files); season packs only for Download Season.
 */
suspend fun ApiClient.downloadSources(
    query: String, title: DetailsTarget, numbering: EpisodeNumbering?, seasonPacksOnly: Boolean,
): List<SourceResult> {
    val q = buildMap {
        put("q", query)
        put("titleId", title.titleId)
        put("mediaType", if (title.isMovie) "movie" else "series")
        put("userId", profileId)
        put("includeSourceHealth", "true")
        put("limit", "50")
        put("downloadRanking", "true")
        title.imdbId?.let { put("imdbId", it) }
        title.year?.let { put("year", it.toString()) }
        if (seasonPacksOnly) put("seasonPacks", "true")
        numbering?.let { n ->
            put("episodeNumberingId", n.seriesId)
            n.ordering?.takeIf { it.isNotEmpty() }?.let { put("episodeOrdering", it) }
        }
    }
    val raw = sendRaw("GET", "/api/indexers/search", q, timeoutMs = 90_000, headers = ThroughputStore.headers())
    val list = json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
    return list.mapIndexedNotNull { i, e -> (e as? JsonObject)?.let { SourceResult.from(it, i) } }
}

@Serializable
data class BatchResolution(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val resolution: Resolution? = null,
    val error: String? = null,
) {
    @Serializable
    data class Resolution(val webdavPath: String? = null, val fileSize: Long? = null)
}

@Serializable
private data class BatchResponse(val results: List<BatchResolution>? = null)

/** Resolves one season pack for several episodes in one round trip (`/api/playback/resolve-batch`). */
suspend fun ApiClient.resolveBatch(result: SourceResult, episodes: List<Episode>): List<BatchResolution> {
    val body = buildJsonObject {
        put("result", result.raw)
        put("episodes", buildJsonArray {
            episodes.forEach { ep ->
                add(buildJsonObject {
                    put("seasonNumber", ep.seasonNumber)
                    put("episodeNumber", ep.episodeNumber)
                    put("episodeCode", ep.code)
                    ep.absoluteEpisodeNumber?.let { put("absoluteEpisodeNumber", it) }
                    ep.airedDate?.let { put("airDate", it) }
                })
            }
        })
    }
    // A usenet pack is imported before episodes are picked, which can take minutes.
    val timeout = if (result.serviceType.equals("debrid", ignoreCase = true)) 120_000L else 900_000L
    val raw = sendRaw("POST", "/api/playback/resolve-batch", body = body, timeoutMs = timeout)
    return json.decodeFromString<BatchResponse>(raw).results.orEmpty()
}

@Serializable
private data class WatchedKeys(val watched: List<String>? = null)

/** The profile's watched items as keys (`movie:<id>`, `episode:<id>`), lowercased. */
suspend fun ApiClient.watchedKeys(): Set<String> =
    send<WatchedKeys>("GET", "$profilePath/history/watched", mapOf("fields" to "status", "format" to "keys"), timeoutMs = 60_000)
        .watched.orEmpty().map { it.lowercase() }.toSet()

/** A source result aimed at one episode of a pack, for resolving episodes one by one. */
fun SourceResult.targeting(ep: Episode): SourceResult? {
    val attributes = buildJsonObject {
        (raw["attributes"] as? JsonObject)?.forEach { (k, v) -> put(k, v) }
        put("targetSeason", ep.seasonNumber.toString())
        put("targetEpisode", ep.episodeNumber.toString())
        put("targetEpisodeCode", ep.code)
    }
    val copy = JsonObject(raw + ("attributes" to attributes))
    return SourceResult.from(copy, index)
}
