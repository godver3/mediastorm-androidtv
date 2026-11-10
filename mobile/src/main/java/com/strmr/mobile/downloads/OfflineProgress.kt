package com.strmr.mobile.downloads

import android.content.Context
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.StartPoint
import com.strmr.tv.api.reportProgress
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.ProgressSink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * What playing a download leaves on the device: its resume point (so a download resumes
 * offline) and the progress reports the server didn't take, the latest per title or episode,
 * sent once it answers again ([flush], on reconnect and when Downloads opens).
 */
object OfflineProgress {
    private val prefs by lazy { App.context.getSharedPreferences("strmr.downloads.progress", Context.MODE_PRIVATE) }
    private val positions = MapSerializer(String.serializer(), Double.serializer())
    private val bodies = MapSerializer(String.serializer(), JsonObject.serializer())
    private val lock = Any()
    private val flushing = Mutex()

    /** The sink for one download's playback. */
    fun sink(item: DownloadItem): ProgressSink = object : ProgressSink {
        override fun saved(positionSeconds: Double, durationSeconds: Double, ended: Boolean) {
            // Finished (or nearly): next time starts over, as the server's resume rule does.
            val keep = !ended && durationSeconds > 0 && positionSeconds >= 30 && positionSeconds / durationSeconds < 0.95
            update(POSITIONS, positions) { if (keep) it + (item.id to positionSeconds) else it - item.id }
        }

        override fun undelivered(body: JsonObject) {
            update(PENDING, bodies) { it + (key(body, item) to body) }
        }
    }

    /** The resume point kept on the device, if any. */
    fun resumePoint(item: DownloadItem): StartPoint? =
        read(POSITIONS, positions)[item.id]?.let { StartPoint.Seconds(it) }

    fun forget(id: String) = update(POSITIONS, positions) { it - id }

    val hasPending: Boolean get() = read(PENDING, bodies).isNotEmpty()

    /** Sends the held reports, oldest first; keeps any the server still doesn't take. */
    suspend fun flush() = flushing.withLock {
        val held = read(PENDING, bodies)
        if (held.isEmpty()) return@withLock
        val ordered = held.entries.sortedBy { it.value["timestamp"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        for ((key, body) in ordered) {
            try {
                ApiClient.reportProgress(body)
                update(PENDING, bodies) { if (it[key] == body) it - key else it }
            } catch (e: Exception) {
                AppLog.w("Downloads", "offline progress still not delivered: ${e.message}")
                return@withLock
            }
        }
        AppLog.i("Downloads", "sent ${ordered.size} offline progress report(s)")
    }

    private fun key(body: JsonObject, item: DownloadItem): String =
        body["itemId"]?.jsonPrimitive?.contentOrNull ?: item.progressItemId

    private fun <T> read(name: String, serializer: kotlinx.serialization.KSerializer<Map<String, T>>): Map<String, T> =
        synchronized(lock) {
            prefs.getString(name, null)?.let { runCatching { ApiJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyMap()
        }

    private fun <T> update(name: String, serializer: kotlinx.serialization.KSerializer<Map<String, T>>, change: (Map<String, T>) -> Map<String, T>) =
        synchronized(lock) {
            val next = change(read(name, serializer))
            prefs.edit().putString(name, ApiJson.encodeToString(serializer, next)).apply()
        }

    private const val POSITIONS = "positions"
    private const val PENDING = "pending"
}
