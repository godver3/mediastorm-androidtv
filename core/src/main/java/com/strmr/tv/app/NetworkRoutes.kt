package com.strmr.tv.app

import com.strmr.tv.api.ApiClient
import com.strmr.tv.remote.RemoteAccess
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Which address requests go to as a phone moves between its home network, other networks and
 * the iroh tunnel (the iPhone app's `AppSession+Routes`). The phone app's connection selector
 * decides; these only switch. The TV app never calls them, so it keeps one route per launch.
 * Main thread only.
 */
object NetworkRoutes {
    /** Paired over iroh, but talking straight to the home server's LAN address (verified first). */
    val isHomeDirect = MutableStateFlow(false)

    /** Unreachable on purpose: where requests go when the tunnel is down (never a LAN fallback). */
    private val unreachableLoopback = "http://127.0.0.1:9/".toHttpUrl()

    /** The address saved by Change Server; switching never overwrites it. */
    val savedServerUrl: HttpUrl? get() = SessionPrefs.server

    /** Whether a pairing is saved (remote access is this device's way in). */
    val isPaired: Boolean get() = RemoteSession.paired

    /** Paired, on the home network: requests go to `url`, which proved to be the paired server. */
    fun routeHomeDirect(url: HttpUrl) {
        RemoteSession.isRemote.value = false
        isHomeDirect.value = true
        ApiClient.remoteCredential = null
        use(url)
    }

    /**
     * Back onto the iroh tunnel. The bridge is sticky: when it can't start, requests go to an
     * unreachable loopback address rather than silently to the LAN, and this throws.
     */
    suspend fun routeRemoteTunnel() {
        isHomeDirect.value = false
        RemoteSession.isRemote.value = true
        ApiClient.remoteCredential = RemoteAccess.pairing?.credential
        try {
            RemoteSession.reconnectOrThrow()
        } catch (e: Exception) {
            if (AppSession.serverUrl.value?.host != "127.0.0.1") use(unreachableLoopback)
            throw e
        }
    }

    /**
     * A typed (unpaired) server: the home or away address from the server's network settings,
     * used until the network changes; not saved.
     */
    fun routeNetworkUrl(url: HttpUrl) {
        if (RemoteSession.isRemote.value || isHomeDirect.value) return
        use(url)
    }

    /** The tunnel again (after sleep or a failure); throws when it doesn't come up. */
    suspend fun reconnectTunnel() = RemoteSession.reconnectOrThrow()

    private fun use(url: HttpUrl) {
        AppSession.serverUrl.value = url
        ApiClient.baseUrl = url
    }
}
