package com.strmr.mobile.connection

import android.content.Context
import com.strmr.tv.api.ApiJson
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Low-volume connection breadcrumbs (the iPhone app's `MobileReconnectJournal`): the last 60
 * events (network changes, route choices, retries), sanitized (no invites, URLs or tokens),
 * kept across launches for Connection settings, and written to the app log, which the log
 * upload sends to the server.
 */
object ReconnectJournal {
    @Serializable
    data class Entry(val timestamp: Long, val event: String, val details: Map<String, String>? = null)

    private const val KEY = "journal.v1"
    private const val MAX_ENTRIES = 60
    private val serializer = ListSerializer(Entry.serializer())
    private val prefs by lazy { App.context.getSharedPreferences("strmr.connection", Context.MODE_PRIVATE) }
    private var cache: List<Entry>? = null

    val entries: List<Entry> get() = synchronized(this) { cache ?: load().also { cache = it } }

    fun record(event: String, details: Map<String, String> = emptyMap()) = synchronized(this) {
        val clean = details.mapValues { sanitize(it.value) }
        val next = (entries + Entry(System.currentTimeMillis(), event, clean.ifEmpty { null })).takeLast(MAX_ENTRIES)
        cache = next
        runCatching { prefs.edit().putString(KEY, ApiJson.encodeToString(serializer, next)).apply() }
        val suffix = clean.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value}" }
        AppLog.i("Reconnect", if (suffix.isEmpty()) event else "$event $suffix")
    }

    private fun load(): List<Entry> =
        prefs.getString(KEY, null)?.let { runCatching { ApiJson.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private val rules = listOf(
        Regex("mshost-iroh-[^\\s\"']+") to "[invite]",
        Regex("https?://[^\\s\"']+", RegexOption.IGNORE_CASE) to "[url]",
        Regex("Bearer\\s+[^\\s\"']+", RegexOption.IGNORE_CASE) to "Bearer [redacted]",
        Regex("(token|password|api[_-]?key)(\\s*[=:]\\s*)[^\\s,\"'}]+", RegexOption.IGNORE_CASE) to "$1$2[redacted]",
    )

    /** Strips what the Expo journal strips: iroh invites, URLs, bearer tokens and secrets. */
    fun sanitize(value: String): String = rules.fold(value) { text, (re, with) -> re.replace(text, with) }.take(240)
}
