package com.strmr.tv.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class TitleSourceCoordinate(val titleId: String, val mediaType: String)
@Serializable
data class TitleSourceDemand(val version: Long, val titles: List<TitleSourceCoordinate> = emptyList())
@Serializable
data class TitleSourceItem(val titleId: String, val mediaType: String, val sourceAvailability: SourceAvailability? = null)
@Serializable
data class TitleSourceResponse(val enabled: Boolean = false, val version: Long = 0, val items: List<TitleSourceItem> = emptyList())

/** An explicit empty titles array releases the view even with default JSON settings. */
fun TitleSourceDemand.toTitleSourceBody(): JsonObject = buildJsonObject {
    put("version", JsonPrimitive(version))
    put("titles", JsonArray(titles.map { title -> buildJsonObject {
        put("titleId", JsonPrimitive(title.titleId)); put("mediaType", JsonPrimitive(title.mediaType))
    } }))
}
