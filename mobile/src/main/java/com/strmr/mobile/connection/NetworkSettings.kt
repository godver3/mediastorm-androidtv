package com.strmr.mobile.connection

import android.content.Context
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiJson
import com.strmr.tv.app.App
import com.strmr.tv.app.Config
import com.strmr.tv.auth.ServerAddress
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

/**
 * The server's "Network URL Switching" settings (web UI → Settings → Server), read as the
 * iPhone and Expo apps read them: this device's own (`/api/clients/{id}/settings`, top level),
 * else the profile's (`network` in its settings), else the server's (`network` in
 * `/api/settings`), whichever first names a home Wi-Fi. Cached for launches that can't reach
 * the server.
 */
@Serializable
data class NetworkSettings(
    val homeWifiSsid: String = "",
    val homeBackendUrl: String = "",
    val remoteBackendUrl: String = "",
    val source: Source = Source.SERVER,
) {
    enum class Source(val title: String) { DEVICE("This Device"), PROFILE("This Profile"), SERVER("Everyone") }

    val isConfigured: Boolean get() = homeWifiSsid.isNotEmpty() && (homeBackendUrl.isNotEmpty() || remoteBackendUrl.isNotEmpty())

    /** The home address as a base URL, unless it is missing or a loopback. */
    val homeUrl: HttpUrl? get() = address(homeBackendUrl)
    val remoteUrl: HttpUrl? get() = address(remoteBackendUrl)

    @Serializable
    private data class Fields(val homeWifiSSID: String? = null, val homeBackendUrl: String? = null, val remoteBackendUrl: String? = null)

    @Serializable
    private data class Nested(val network: Fields? = null)

    companion object {
        private val prefs by lazy { App.context.getSharedPreferences("strmr.connection", Context.MODE_PRIVATE) }
        private const val CACHE_KEY = "networkSettings"

        /** The address as a base URL; loopback addresses mean nothing to another device. */
        fun address(text: String): HttpUrl? {
            if (text.isBlank()) return null
            val url = ServerAddress.normalize(text) ?: return null
            val host = url.host.lowercase()
            if (host == "localhost" || host == "::1" || host == "0.0.0.0" || host.startsWith("127.")) return null
            return url
        }

        val cached: NetworkSettings?
            get() = prefs.getString(CACHE_KEY, null)?.let { runCatching { ApiJson.decodeFromString(serializer(), it) }.getOrNull() }

        /** Null clears the cache (no section names a home network). */
        fun cache(settings: NetworkSettings?) {
            val edit = prefs.edit()
            if (settings == null) edit.remove(CACHE_KEY) else edit.putString(CACHE_KEY, ApiJson.encodeToString(serializer(), settings))
            edit.apply()
        }

        /** The effective section, or null when none names a home Wi-Fi; throws when nothing answered. */
        suspend fun fetch(): NetworkSettings? = coroutineScope {
            val api = ApiClient
            val device = async {
                runCatching {
                    api.send<Fields>("GET", "/api/clients/${api.segment(Config.clientId)}/settings", mapOf("userId" to api.profileId),
                                     promptOnLock = false, timeoutMs = 10_000)
                }.getOrNull()
            }
            val profile = async { runCatching { api.send<Nested>("GET", "${api.profilePath}/settings", promptOnLock = false, timeoutMs = 10_000) }.getOrNull() }
            val server = async { runCatching { api.send<Nested>("GET", "/api/settings", promptOnLock = false, timeoutMs = 10_000) }.getOrNull() }
            val d = device.await()
            val p = profile.await()
            val s = server.await()
            if (d == null && p == null && s == null) throw IOException("The server didn't answer.")
            listOf(d to Source.DEVICE, p?.network to Source.PROFILE, s?.network to Source.SERVER).firstNotNullOfOrNull { (f, source) ->
                val ssid = f?.homeWifiSSID?.trim().orEmpty()
                if (f == null || ssid.isEmpty()) null
                else NetworkSettings(ssid, f.homeBackendUrl?.trim().orEmpty(), f.remoteBackendUrl?.trim().orEmpty(), source)
            }
        }
    }
}
