package com.strmr.tv.api

import com.strmr.tv.adaptive.ThroughputStore
import com.strmr.tv.app.Config
import com.strmr.tv.player.SubtitleStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Prequeue, streams, progress and the profile's playback settings.

/** Carries this route's throughput estimate, as Expo's `prequeuePlayback` does, for adaptive caps. */
suspend fun ApiClient.startPrequeue(body: JsonObject): PrequeueStartResponse =
    send("POST", "/api/playback/prequeue", body = body, headers = ThroughputStore.headers())

suspend fun ApiClient.prequeueStatus(id: String): PrequeueStatus =
    send("GET", "/api/playback/prequeue/${segment(id)}")

/** Best effort; a failed report is retried by the next one. */
suspend fun ApiClient.reportProgress(body: JsonObject) {
    runCatching { sendRaw("POST", "$profilePath/history/progress", body = body, promptOnLock = false) }
}

/**
 * Raw-file stream URL for the player. transmux=0 keeps the backend from remuxing: ExoPlayer
 * demuxes MKV / MP4 itself. Auth travels in [mediaHeaders]; the profile unlock also rides in
 * the query, so it survives anything that drops custom headers. `profileId` and the media
 * metadata let the stream tracker attribute the stream on the admin dashboard.
 */
fun ApiClient.streamUrl(streamPath: String, metadata: Map<String, String> = emptyMap()): String {
    val query = metadata.toMutableMap()
    query["path"] = streamPath
    query["transmux"] = "0"
    query["profileId"] = profileId
    profileName?.takeIf { it.isNotEmpty() }?.let { query["profileName"] = it }
    unlocks.token(profileId)?.let { query["profileUnlock"] = it }
    return url("/api/video/stream", query).toString()
}

/** Headers the player attaches to every media fetch. */
val ApiClient.mediaHeaders: Map<String, String>
    get() = buildMap {
        put("X-Client-ID", Config.clientId)
        remoteCredential?.let { put("X-Remote-Access-Credential", it) }
        token?.let { put("Authorization", "Bearer $it") }
        unlocks.token(profileId)?.let { put(ApiClient.UNLOCK_HEADER, it) }
    }

/**
 * The playback settings this client reads, layered as the Expo app layers them: this
 * device's override from `/api/clients/{id}/settings`, then the profile's settings (defaults
 * already filled in by the server).
 */
data class PlaybackSettings(
    /** Streams start resolving as a details page opens (`playback.disablePrequeue` off). */
    val prequeueOnOpen: Boolean = true,
    /** The details page plays the trailer behind the header (`playback.autoPlayTrailersTV`). */
    val autoPlayTrailers: Boolean = false,
    /** Next Episode counts down over the credits and plays by itself (`playback.creditsAutoSkip`). */
    val creditsAutoSkip: Boolean = false,
    /** The Expo app's subtitle style (`playback.subtitle*`) under this device's; null from servers that send none. */
    val subtitleStyle: SubtitleStyle.Server? = null,
    /** Subtitle language (`playback.preferredSubtitleLanguage`), for when the server sent no track choice. */
    val preferredSubtitleLanguage: String? = null,
    /** off | forced-only | on (the older always / auto mapped), for when the server sent no track choice. */
    val preferredSubtitleMode: String? = null,
    /** Audio language (`playback.preferredAudioLanguage`). */
    val preferredAudioLanguage: String? = null,
    /** The languages the player's menus offer (`playback.allowedTrackLanguages`); empty allows all. */
    val allowedTrackLanguages: List<String> = emptyList(),
)

@Serializable
private data class PlaybackFields(
    val disablePrequeue: Boolean? = null,
    val autoPlayTrailersTV: Boolean? = null,
    val creditsAutoSkip: Boolean? = null,
    /** The older name of `creditsAutoSkip`. */
    val creditsDetection: Boolean? = null,
    val subtitleSize: Double? = null,
    val subtitleColor: String? = null,
    val subtitleOpacity: Double? = null,
    val subtitleBold: Boolean? = null,
    val subtitleOutlineEnabled: Boolean? = null,
    val subtitleOutlineColor: String? = null,
    val subtitleOutlineWeight: Double? = null,
    val subtitleBackgroundEnabled: Boolean? = null,
    val subtitleBackgroundColor: String? = null,
    val subtitleBackgroundOpacity: Double? = null,
    val preferredSubtitleLanguage: String? = null,
    val preferredSubtitleMode: String? = null,
    val preferredAudioLanguage: String? = null,
    val allowedTrackLanguages: List<String>? = null,
) {
    /** The `subtitle*` fields of the same object. */
    val style: SubtitleStyle.Server
        get() = SubtitleStyle.Server(
            subtitleSize, subtitleColor, subtitleOpacity, subtitleBold, subtitleOutlineEnabled, subtitleOutlineColor,
            subtitleOutlineWeight, subtitleBackgroundEnabled, subtitleBackgroundColor, subtitleBackgroundOpacity,
        )

    val autoSkip: Boolean? get() = creditsAutoSkip ?: creditsDetection
}

@Serializable
private data class UserPlayback(val playback: PlaybackFields? = null)

suspend fun ApiClient.playbackSettings(): PlaybackSettings = coroutineScope {
    val user = async {
        runCatching { send<UserPlayback>("GET", "$profilePath/settings", promptOnLock = false, timeoutMs = 10_000) }.getOrNull()?.playback
    }
    val device = async {
        runCatching {
            send<PlaybackFields>("GET", "/api/clients/${segment(Config.clientId)}/settings", mapOf("userId" to profileId),
                                 promptOnLock = false, timeoutMs = 10_000)
        }.getOrNull()
    }
    val (u, d) = user.await() to device.await()
    PlaybackSettings(
        prequeueOnOpen = !(d?.disablePrequeue ?: u?.disablePrequeue ?: false),
        autoPlayTrailers = d?.autoPlayTrailersTV ?: u?.autoPlayTrailersTV ?: false,
        creditsAutoSkip = d?.autoSkip ?: u?.autoSkip ?: false,
        subtitleStyle = d?.style?.mergedOver(u?.style) ?: u?.style,
        preferredSubtitleLanguage = (d?.preferredSubtitleLanguage?.ifEmpty { null } ?: u?.preferredSubtitleLanguage)?.ifEmpty { null },
        preferredSubtitleMode = when (val mode = d?.preferredSubtitleMode?.ifEmpty { null } ?: u?.preferredSubtitleMode) {
            "always" -> "on"
            "auto" -> "forced-only"
            "" -> null
            else -> mode
        },
        preferredAudioLanguage = (d?.preferredAudioLanguage?.ifEmpty { null } ?: u?.preferredAudioLanguage)?.ifEmpty { null },
        allowedTrackLanguages = d?.allowedTrackLanguages?.ifEmpty { null } ?: u?.allowedTrackLanguages ?: emptyList(),
    )
}
