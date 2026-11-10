package com.strmr.tv.live

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.streamUrl
import kotlinx.serialization.Serializable

/**
 * The provider's catch-up for a channel ([LiveChannel.catchup]): it keeps [days] of past
 * programmes and replays them on request. Nothing is recorded; the server only turns a guide
 * programme into the provider's archive URL (`GET /api/live/catchup`).
 */
@Serializable
data class LiveCatchup(val days: Int = 0, val mode: String? = null)

/**
 * `GET /api/live/catchup`: the provider URL for a programme, from [offsetSeconds] in (the
 * provider may round down to a whole minute), running [durationSeconds].
 */
@Serializable
data class LiveCatchupStart(val url: String, val offsetSeconds: Int = 0, val durationSeconds: Int = 0)

object LiveCatchups {
    private const val DAY_MS = 86_400_000L

    /** How far back the provider replays [channel]; 0 without catch-up. */
    fun window(channel: LiveChannel): Long = (channel.catchup?.days ?: 0).coerceAtLeast(0) * DAY_MS

    /** The provider can replay [program] from its start: it has started and is in the archive. */
    fun canReplay(channel: LiveChannel, program: EpgProgram?, now: Long = System.currentTimeMillis()): Boolean {
        val window = window(channel)
        if (program == null || window <= 0 || program.startMs <= 0) return false
        return program.startMs < now - 60_000 && program.startMs >= now - window
    }

    /** It has ended and can be replayed: Select plays the archive instead of tuning live. */
    fun replaysOnSelect(channel: LiveChannel, program: EpgProgram?, now: Long = System.currentTimeMillis()) =
        program != null && program.stopMs <= now && canReplay(channel, program, now)

    /**
     * How far back the guide pages: the longest archive among [channels] (capped at a week, the
     * server's default EPG retention), else the half hour it always kept.
     */
    fun guideHistory(channels: List<LiveChannel>): Long =
        (channels.maxOfOrNull { window(it) } ?: 0L).coerceIn(30 * 60_000L, 7 * DAY_MS)
}

/** The provider's archive URL for [program] on [channel], from [offset] seconds in. */
suspend fun ApiClient.liveCatchup(channel: LiveChannel, program: EpgProgram, offset: Int = 0): LiveCatchupStart {
    val query = mutableMapOf(
        "channelId" to channel.id, "profileId" to profileId,
        "start" to program.start, "stop" to program.stop, "offset" to offset.coerceAtLeast(0).toString(),
    )
    channel.sourceId?.let { query["sourceId"] = it }
    return send("GET", "/api/live/catchup", query, timeoutMs = 30_000)
}

/**
 * The archive as a file through the server's stream proxy: providers serve it whole, with a
 * length and byte ranges (an Xtream timeshift is one TS file), so it seeks as VOD.
 */
fun ApiClient.catchupStreamUrl(archive: LiveCatchupStart, channel: LiveChannel, program: EpgProgram): String =
    streamUrl(archive.url, mapOf("mediaType" to "channel", "title" to program.title, "channelName" to channel.name))
