package com.strmr.tv.live

import android.content.Context
import android.os.SystemClock
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import java.net.URLEncoder

/**
 * Live TV's remembered "this provider needs HLS" (Expo `livePlaybackPreference`, tvOS
 * `LiveHLSPreference`). When a direct stream won't start the player retries once as an HLS
 * session ([com.strmr.tv.player.LiveHlsFallback]); once that has played for a while the choice
 * is kept on this device for the server and live source, and later tunes of that source ask
 * for HLS at once. Settings › Reset Live TV Playback clears it.
 */
object LiveHlsPreference {
    /** The server (its remote-access identity) and live source a preference belongs to. */
    data class Scope(val server: String, val sourceId: String)

    private val prefs by lazy { App.context.getSharedPreferences("strmr.liveHls", Context.MODE_PRIVATE) }
    private const val PREFIX = "live.hlsPreference.v1:"

    @Serializable
    private data class Identity(val serverId: String? = null)

    private var serverId: String? = null
    /** The base URL [serverId] was looked up on; another server is looked up afresh. */
    private var identityBase: HttpUrl? = null
    private var lookup: Deferred<String?>? = null
    private var retryAt = 0L

    /**
     * The channel's scope. With [wait] false it answers from the cached server identity only
     * (starting the lookup), so a tune is never held up by it.
     */
    suspend fun scope(channel: LiveChannel, wait: Boolean): Scope? {
        val source = channel.sourceId?.trim()?.ifEmpty { null } ?: return null
        val pending = synchronized(this) {
            resetIfServerChanged()
            serverId?.let { return Scope(it, source) }
            startLookup()
        }
        val id = if (wait) pending?.await() else null
        return id?.let { Scope(it, source) }
    }

    /** True when the channel's source is remembered as needing HLS (cached identity only). */
    suspend fun prefersHls(channel: LiveChannel): Boolean = scope(channel, wait = false)?.let(::prefersHls) == true

    fun prefersHls(scope: Scope): Boolean = prefs.getString(key(scope), null) == "hls"

    fun remember(scope: Scope) {
        prefs.edit().putString(key(scope), "hls").apply()
    }

    /** Forgets every remembered source; returns how many there were. */
    fun resetAll(): Int {
        val keys = prefs.all.keys.filter { it.startsWith(PREFIX) }
        prefs.edit().apply { keys.forEach(::remove) }.apply()
        return keys.size
    }

    internal fun key(scope: Scope): String =
        PREFIX + URLEncoder.encode(scope.server, "UTF-8") + ":" + URLEncoder.encode(scope.sourceId, "UTF-8")

    private fun resetIfServerChanged() {
        val base = ApiClient.baseUrl
        if (identityBase == base) return
        identityBase = base
        serverId = null
        lookup = null
        retryAt = 0L
    }

    /** One lookup at a time; a failed one isn't retried for five minutes. */
    private fun startLookup(): Deferred<String?>? {
        lookup?.let { return it }
        if (SystemClock.elapsedRealtime() < retryAt) return null
        val base = identityBase
        val result = CompletableDeferred<String?>()
        lookup = result
        MainScope().launch {
            val id = runCatching {
                ApiClient.send<Identity>("GET", "/api/remote-access/identity", promptOnLock = false, timeoutMs = 5_000).serverId
            }.onFailure { AppLog.w("Live", "server identity unavailable: ${it.message}") }.getOrNull()?.ifEmpty { null }
            synchronized(this@LiveHlsPreference) {
                if (identityBase == base) {
                    if (id != null) serverId = id else retryAt = SystemClock.elapsedRealtime() + 5 * 60_000
                    lookup = null
                }
            }
            result.complete(if (identityBase == base) id else null)
        }
        return result
    }
}
