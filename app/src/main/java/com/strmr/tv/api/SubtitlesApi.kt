package com.strmr.tv.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Online subtitles: search and download through the server's providers (OpenSubtitles,
// SubDL, SubSource), machine translation of a WebVTT source, and extraction of an embedded
// track to WebVTT (the translation's English source).

/** One `/api/subtitles/search` result. */
@Serializable
data class SubtitleSearchResult(
    val id: String,
    val provider: String,
    val language: String = "",
    val release: String = "",
    val downloads: Int = 0,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerialName("page_link") val pageLink: String? = null,
)

/** What the search and download identify the title by. */
data class SubtitleQuery(
    val imdbId: String?,
    val title: String?,
    val releaseName: String?,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
) {
    fun params(language: String): MutableMap<String, String> = buildMap {
        imdbId?.takeIf { it.isNotEmpty() }?.let { put("imdbId", it) }
        title?.takeIf { it.isNotEmpty() }?.let { put("title", it) }
        year?.takeIf { it > 0 }?.let { put("year", it.toString()) }
        season?.let { put("season", it.toString()) }
        episode?.let { put("episode", it.toString()) }
        put("language", language)
    }.toMutableMap()
}

suspend fun ApiClient.searchSubtitles(query: SubtitleQuery, language: String): List<SubtitleSearchResult> {
    val params = query.params(language)
    query.releaseName?.takeIf { it.isNotEmpty() }?.let { params["releaseName"] = it }
    return send("GET", "/api/subtitles/search", params, promptOnLock = false, timeoutMs = 60_000)
}

/** The chosen result as WebVTT. */
suspend fun ApiClient.downloadSubtitle(result: SubtitleSearchResult, query: SubtitleQuery, language: String): String {
    val params = query.params(language)
    params["subtitleId"] = result.id
    params["provider"] = result.provider
    return sendRaw("GET", "/api/subtitles/download", params, promptOnLock = false, timeoutMs = 60_000)
}

/**
 * `sourcePath` (a server-relative `/api/...` WebVTT URL, which the server fetches from its own
 * loopback with this request's Authorization) translated to `targetLanguage`. Incremental:
 * each call translates more cues and returns what is done so far.
 */
suspend fun ApiClient.translateSubtitle(sourcePath: String, targetLanguage: String, sourceLanguage: String = "en"): String =
    sendRaw(
        "GET", "/api/subtitles/translate",
        mapOf("sourceUrl" to sourcePath, "targetLanguage" to targetLanguage, "sourceLanguage" to sourceLanguage, "userId" to profileId),
        promptOnLock = false, timeoutMs = 60_000,
    )

@Serializable
data class SubtitleExtractSession(val sessionId: String = "", val subtitleUrl: String = "", val firstCueTime: Double? = null)

/**
 * Starts extracting embedded subtitle track `subtitleTrack` (its position among the file's
 * subtitle streams) to WebVTT from `startOffset` seconds; `subtitleUrl` is server-relative.
 */
suspend fun ApiClient.startSubtitleExtract(path: String, subtitleTrack: Int, startOffset: Double): SubtitleExtractSession {
    val query = buildMap {
        put("path", path)
        put("subtitleTrack", subtitleTrack.toString())
        if (startOffset > 0) put("startOffset", "%.3f".format(java.util.Locale.ROOT, startOffset))
        put("format", "vtt")
    }
    return send("GET", "/api/video/subtitles/start", query, promptOnLock = false, timeoutMs = 60_000)
}

/** The server's subtitle settings that decide what the player may try. */
data class SubtitleServerSettings(val providerConfigured: Boolean, val translationEnabled: Boolean)

@Serializable
private data class SubtitleSettingsEnvelope(val subtitles: Fields? = null) {
    @Serializable
    data class Fields(
        val openSubtitlesUsername: String? = null,
        val openSubtitlesPassword: String? = null,
        val subdlApiKey: String? = null,
        val subsourceApiKey: String? = null,
        val enableTranslatedSubs: Boolean? = null,
    )
}

/**
 * `/api/settings` `subtitles` (secrets come back masked but non-empty when set). Unreadable
 * settings count as no provider, translation on, as in the Expo app.
 */
suspend fun ApiClient.subtitleServerSettings(): SubtitleServerSettings {
    val s = runCatching { send<SubtitleSettingsEnvelope>("GET", "/api/settings", promptOnLock = false, timeoutMs = 10_000) }
        .getOrNull()?.subtitles
    val provider = s != null && ((!s.openSubtitlesUsername.isNullOrEmpty() && !s.openSubtitlesPassword.isNullOrEmpty()) ||
        !s.subdlApiKey.isNullOrEmpty() || !s.subsourceApiKey.isNullOrEmpty())
    return SubtitleServerSettings(provider, s?.enableTranslatedSubs != false)
}
