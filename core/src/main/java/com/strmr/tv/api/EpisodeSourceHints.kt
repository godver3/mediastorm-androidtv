package com.strmr.tv.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class EpisodeSourceCoordinate(val episodeId: String, val seasonNumber: Int, val episodeNumber: Int)
@Serializable
data class EpisodeSourceDemand(
    val version: Long,
    val titleId: String = "",
    val numbering: EpisodeNumbering? = null,
    val episodes: List<EpisodeSourceCoordinate> = emptyList(),
)
@Serializable
data class EpisodeSourceItem(
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val sourceAvailability: SourceAvailability? = null,
)
@Serializable
data class EpisodeSourceResponse(val enabled: Boolean = false, val version: Long = 0, val items: List<EpisodeSourceItem> = emptyList())

/** Keep the empty episode array explicit: it is the protocol's release operation. */
fun EpisodeSourceDemand.toEpisodeSourceBody(): JsonObject = buildJsonObject {
    put("version", JsonPrimitive(version))
    if (titleId.isNotEmpty()) put("titleId", JsonPrimitive(titleId))
    numbering?.let { n ->
        put("numbering", buildJsonObject {
            put("seriesId", JsonPrimitive(n.seriesId))
            n.ordering?.let { put("ordering", JsonPrimitive(it)) }
        })
    }
    put("episodes", JsonArray(episodes.map { episode ->
        buildJsonObject {
            put("episodeId", JsonPrimitive(episode.episodeId))
            put("seasonNumber", JsonPrimitive(episode.seasonNumber))
            put("episodeNumber", JsonPrimitive(episode.episodeNumber))
        }
    }))
}
