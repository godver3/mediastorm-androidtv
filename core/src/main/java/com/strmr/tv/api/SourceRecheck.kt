package com.strmr.tv.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class SourceRecheckRequest(val titleId: String, val mediaType: String, val numbering: EpisodeNumbering? = null, val episode: EpisodeSourceCoordinate? = null)
@Serializable
data class SourceRecheckResponse(val status: String = "", val retryAfterSeconds: Int = 1)
fun SourceRecheckRequest.toSourceRecheckBody(): JsonObject = buildJsonObject {
    put("titleId", JsonPrimitive(titleId)); put("mediaType", JsonPrimitive(mediaType))
    numbering?.let { n -> put("numbering", buildJsonObject { put("seriesId",JsonPrimitive(n.seriesId)); n.ordering?.let { put("ordering",JsonPrimitive(it)) } }) }
    episode?.let { ep -> put("episode",buildJsonObject { put("episodeId",JsonPrimitive(ep.episodeId));put("seasonNumber",JsonPrimitive(ep.seasonNumber));put("episodeNumber",JsonPrimitive(ep.episodeNumber)) }) }
}
