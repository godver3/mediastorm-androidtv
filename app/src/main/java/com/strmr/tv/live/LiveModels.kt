package com.strmr.tv.live

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** One channel from `/api/live/channels`; favorites and hidden channels are stored by `id`. */
@Serializable
data class LiveChannel(
    val id: String,
    val name: String,
    /** The provider URL; `/api/live/hls/start` tunes it. */
    val url: String,
    val logo: String? = null,
    /** The channel's category. */
    val group: String? = null,
    /** The EPG key; channels without one have no guide data. */
    val tvgId: String? = null,
    val tvgName: String? = null,
    val sourceId: String? = null,
    val sourceName: String? = null,
    /** Provider-local id, sent as `channelId` when tuning. */
    val playbackId: String? = null,
    /**
     * The stream picked under Choose Source, for a channel that is a Stremio addon's stream
     * list; sent as `stremioStreamIndex` when tuning (null lets the server pick).
     */
    val stremioStreamIndex: Int? = null,
) {
    /** A Stremio addon stream resource (`…/stream/….json`), which can list several streams (Expo `isStremioStreamResourceUrl`). */
    val hasStremioSources: Boolean
        get() {
            val path = url.substringBefore('?')
            return url.startsWith("http") && path.contains("/stream/") && path.endsWith(".json")
        }

    /** The key its EPG data is filed under (`tvgId`, else the channel id, as the Expo guide does). */
    val guideKey: String get() = tvgId?.ifEmpty { null } ?: id

    /** Playlists often link GitHub's HTML page for an image, rewritten to the raw file. */
    val logoUrl: String?
        get() {
            val l = logo?.ifEmpty { null } ?: return null
            return if (l.startsWith("https://github.com/") && l.contains("/blob/")) {
                l.replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/")
            } else l
        }
}

@Serializable
data class LiveSource(val id: String, val name: String)

/** Choose Source for a Stremio channel: the addon's streams, one of which is tuned with `stremioStreamIndex`. */
data class LiveSourceChoice(val channel: LiveChannel, val streams: List<LiveStremioStream>)

/** One stream of a Stremio channel (`GET /api/live/stremio/streams`), by its index in the addon's list. */
@Serializable
data class LiveStremioStream(
    val index: Int,
    val label: String,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
)

/** `POST /api/live/channels`: one page of the filtered list, favorites first. */
@Serializable
data class LiveChannelsPage(
    val channels: List<LiveChannel> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val availableCategories: List<String>? = null,
    val sources: List<LiveSource>? = null,
)

/** A programme from the EPG; the server has already applied the profile's time offset. */
@Serializable
data class EpgProgram(
    val title: String,
    val description: String? = null,
    val start: String,
    val stop: String,
    val icon: String? = null,
    val categories: List<String>? = null,
    val episode: String? = null,
) {
    /** Epoch milliseconds; a programme that doesn't parse has no length. */
    @Transient val startMs: Long = parse(start)
    @Transient val stopMs: Long = parse(stop)
    /** The stop as the guide shows it: cut where the next programme starts (see LiveStore.merge). */
    @Transient var endMs: Long = stopMs

    fun isAiring(now: Long = System.currentTimeMillis()) = now in startMs until endMs

    fun progress(now: Long = System.currentTimeMillis()): Float {
        val length = endMs - startMs
        return if (length <= 0) 0f else ((now - startMs).toFloat() / length).coerceIn(0f, 1f)
    }
}

// Top level, not a private companion: kotlinx.serialization looks up `EpgProgram.serializer()`
// on the companion, and a private one throws IllegalAccessError when decoded from elsewhere.
private fun parse(text: String) = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrDefault(0L)

/** `POST /api/live/epg/now`: one entry per requested key. */
@Serializable
data class EpgNowPlaying(val channelId: String, val current: EpgProgram? = null, val next: EpgProgram? = null)

/** A tuned channel: the URL to play, and the server HLS session to keep alive (none when direct). */
data class LiveStream(val url: String, val sessionId: String?)
