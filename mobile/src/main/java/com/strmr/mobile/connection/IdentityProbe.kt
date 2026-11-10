package com.strmr.mobile.connection

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.await
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Whether an address is the paired server: `GET /api/remote-access/identity` within 2 s,
 * without credentials or redirects, answering with the invite's endpoint id. No credentials
 * ever go to an address before it passes.
 */
object IdentityProbe {
    @Serializable
    private data class Identity(val serverId: String? = null)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(2, TimeUnit.SECONDS).callTimeout(2, TimeUnit.SECONDS).build()
    }

    suspend fun matches(base: HttpUrl, expected: String): Boolean {
        val url = base.newBuilder().addPathSegments("api/remote-access/identity").build()
        val response = runCatching { client.newCall(Request.Builder().url(url).build()).await() }.getOrNull() ?: return false
        if (response.code != 200) return false
        val identity = runCatching { ApiJson.decodeFromString<Identity>(response.text) }.getOrNull() ?: return false
        return RouteRules.identityMatches(identity.serverId, expected)
    }

    /** `/health` at the address in use (through the tunnel when remote), within 6 s. */
    suspend fun health(base: HttpUrl): Boolean {
        val url = base.newBuilder().addPathSegments("health").build()
        val request = Request.Builder().url(url).apply { ApiClient.remoteCredential?.let { header("X-Remote-Access-Credential", it) } }
            .header("X-Client-ID", com.strmr.tv.app.Config.clientId).build()
        val response = runCatching { ApiClient.http.newCall(request).apply { timeout().timeout(6, TimeUnit.SECONDS) }.await() }.getOrNull()
        return response?.isSuccessful == true
    }
}
