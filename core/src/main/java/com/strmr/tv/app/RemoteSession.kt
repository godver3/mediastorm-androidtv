package com.strmr.tv.app

import com.strmr.tv.library.ProfileDataContext
import kotlinx.coroutines.CancellationException

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.logout
import com.strmr.tv.launcher.LauncherSync
import com.strmr.tv.remote.RemoteAccess
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [AppSession]'s iroh remote access around [RemoteAccess]: pairing, connecting at launch and
 * reconnecting. The base URL is then the loopback proxy, whose port can change after sleep
 * or a failure. Main thread only.
 */
internal object RemoteSession {
    val isRemote = MutableStateFlow(false)
    val paired: Boolean get() = RemoteAccess.pairing != null || RemoteAccess.reload()

    /** Pairs with a connection code from the server's admin UI. */
    suspend fun pair(code: String) {
        val expectedContext = ProfileDataContext.current
        val url = RemoteAccess.pair(code)
        ProfileDataContext.requireCurrent(expectedContext)
        val pairing = RemoteAccess.pairing
        // A new pairing is a new server as far as the session goes.
        LauncherSync.clear("paired")
        ProfileDataContext.requireCurrent(expectedContext)
        if (RemoteAccess.pairing !== pairing) throw CancellationException("Pairing changed")
        ApiClient.logout()
        if (RemoteAccess.pairing !== pairing) throw CancellationException("Pairing changed")
        SessionPrefs.forgetAll()
        enter()
        AppSession.useServer(url, pairing?.credential)
        AppSession.moveTo(AppSession.Phase.Login)
    }

    /** Launch with a stored pairing, or Try Again after it failed: the session loads with the proxy. */
    suspend fun start() {
        AppSession.moveTo(AppSession.Phase.Starting)
        enter()
        val expectedContext = ProfileDataContext.current
        val pairing = RemoteAccess.pairing
        try {
            val url = RemoteAccess.connect()
            ProfileDataContext.requireCurrent(expectedContext)
            if (RemoteAccess.pairing !== pairing) throw CancellationException("Pairing changed")
            AppSession.useServer(url, pairing?.credential)
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            ProfileDataContext.requireCurrent(expectedContext)
            AppSession.moveTo(AppSession.Phase.Unavailable(e.message ?: "Couldn't reach the server over remote access."))
            return
        }
        if (ApiClient.session != null) AppSession.resumeSession() else AppSession.moveTo(AppSession.Phase.Login)
    }

    /** The proxy again after sleep or a failure; its port can change, so the base URL follows. */
    suspend fun reconnectOrThrow(expectedContext: Long = ProfileDataContext.current) {
        val pairing = RemoteAccess.pairing
        val url = RemoteAccess.connect()
        ProfileDataContext.requireCurrent(expectedContext)
        if (RemoteAccess.pairing !== pairing) throw CancellationException("Pairing changed")
        AppSession.serverUrl.value = url
        ApiClient.configureRoute(url, pairing?.credential)
    }

    fun leave() {
        RemoteAccess.forget()
        isRemote.value = false
        NetworkRoutes.isHomeDirect.value = false
        ApiClient.remoteCredential = null
    }

    private fun enter() {
        isRemote.value = true
        NetworkRoutes.isHomeDirect.value = false
    }
}
