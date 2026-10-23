package com.strmr.tv.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// Manual source selection

/**
 * One indexer/scraper result. The raw JSON is kept verbatim because the backend's resolve
 * endpoint takes the result back exactly as it was returned.
 */
class SourceResult private constructor(
    val raw: JsonObject,
    /** Position in the server's response; source-health updates are keyed by it. */
    val index: Int,
    val title: String,
) {
    val id: String = raw.str("guid") ?: title
    val indexer: String = raw.str("indexer") ?: ""
    val sizeBytes: Long = runCatching { raw["sizeBytes"]?.jsonPrimitive?.longOrNull }.getOrNull() ?: 0
    val serviceType: String = raw.str("serviceType") ?: ""
    val attributes: Map<String, String> = runCatching {
        raw["attributes"]?.jsonObject?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap()
    }.getOrNull() ?: emptyMap()
    /** Debrid cache / usenet health annotation (`includeSourceHealth=true`). */
    val health: SourceHealth? = runCatching { raw["sourceHealth"]?.jsonObject }.getOrNull()?.let { h ->
        h.str("state")?.let { SourceHealth(it, h.str("token")) }
    }

    companion object {
        fun from(raw: JsonObject, index: Int): SourceResult? = raw.str("title")?.let { SourceResult(raw, index, it) }

        private fun JsonObject.str(key: String) =
            runCatching { (get(key) as? JsonPrimitive)?.contentOrNull }.getOrNull()
    }
}

@Serializable
data class SourceHealth(
    /** healthy | cached | unhealthy | not_cached | pending | unknown */
    val state: String,
    /** Poll token while `state` is pending. */
    val token: String? = null,
)

/** `GET /api/indexers/source-health` long-poll response. */
@Serializable
data class SourceHealthSnapshot(val complete: Boolean = true, val results: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(val index: Int, val health: SourceHealth)
}

@Serializable
data class PlaybackResolution(
    val queueId: Long? = null,
    val webdavPath: String? = null,
    val trackSelection: TrackSelection? = null,
)

// Tracks

/**
 * One container track the server chose (`streamIndex` is the container number,
 * `typeIndex` the position among same-kind tracks).
 */
@Serializable
data class SelectedTrack(
    val streamIndex: Int,
    val typeIndex: Int,
    val language: String? = null,
    val languageCode: String? = null,
    val languageIndex: Int = 0,
    val languageCount: Int = 0,
    val codec: String? = null,
    val title: String? = null,
    val forced: Boolean? = null,
    val bitmap: Boolean? = null,
)

/** The server's audio / subtitle choice for a source, from the profile's preferences. */
@Serializable
data class TrackSelection(
    val audio: SelectedTrack? = null,
    val subtitle: SelectedTrack? = null,
    /** `subtitle` restricted to text codecs (equal to it when it is already text). */
    val textSubtitle: SelectedTrack? = null,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    /** off | forced-only | on */
    val subtitleMode: String? = null,
)

/**
 * One subtitle stream of the file, in container order: its position in the list is what
 * `/api/video/subtitles/start` takes as `subtitleTrack` (`index` carries the container
 * number in prequeue responses).
 */
@Serializable
data class SubtitleStreamInfo(
    val index: Int,
    val absoluteIndex: Int = -1,
    val language: String = "",
    val title: String = "",
    val codec: String = "",
    val forced: Boolean = false,
    val isBitmap: Boolean = false,
)

// Prequeue

@Serializable
data class PrequeueStartResponse(val prequeueId: String, val status: String = "")

@Serializable
data class PrequeueStatus(
    val prequeueId: String,
    val status: String,
    /** The episode being resolved; the server picks the next-up one when none was asked for. */
    val targetEpisode: TargetEpisode? = null,
    val trackSelection: TrackSelection? = null,
    val progressStage: String? = null,
    val progressDetail: String? = null,
    val streamPath: String? = null,
    val displayName: String? = null,
    val duration: Double? = null,
    val error: String? = null,
    /** The file's subtitle streams, in container order. */
    val subtitleTracks: List<SubtitleStreamInfo>? = null,
    /** The file's audio streams; `index` is the container stream number. */
    val audioTracks: List<ProbedAudioTrack>? = null,
    /** The release picked (AIOStreams passthrough name, the indexer result, its attributes). */
    val passthroughName: String? = null,
    val selectedResult: SelectedResult? = null,
    val resultAttributes: Map<String, String>? = null,
) {
    @Serializable
    data class TargetEpisode(val seasonNumber: Int, val episodeNumber: Int)

    @Serializable
    data class SelectedResult(val title: String? = null, val attributes: Map<String, String>? = null)
}
