package com.strmr.tv.app

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
    val paired: Boolean get() = RemoteAccess.pairing != null

    /** Pairs with a connection code from the server's admin UI. */
    suspend fun pair(code: String) {
        val url = RemoteAccess.pair(code)
        // A new pairing is a new server as far as the session goes.
        LauncherSync.clear("paired")
        ApiClient.logout()
        SessionPrefs.forgetAll()
        enter()
        AppSession.useServer(url)
        AppSession.moveTo(AppSession.Phase.Login)
    }

    /** Launch with a stored pairing. */
    suspend fun start() {
        AppSession.moveTo(AppSession.Phase.Starting)
        enter()
        try {
            AppSession.useServer(RemoteAccess.connect())
        } catch (e: Exception) {
            AppSession.moveTo(AppSession.Phase.Unavailable(e.message ?: "Couldn't reach the server over remote access."))
            return
        }
        if (ApiClient.session != null) AppSession.resumeSession() else AppSession.moveTo(AppSession.Phase.Login)
    }

    /** The proxy again after sleep or a failure; its port can change, so the base URL follows. */
    suspend fun reconnectOrThrow() {
        val url = RemoteAccess.connect()
        if (url != AppSession.serverUrl.value) {
            AppSession.serverUrl.value = url
            ApiClient.baseUrl = url
        }
    }

    fun leave() {
        RemoteAccess.forget()
        isRemote.value = false
        ApiClient.remoteCredential = null
    }

    private fun enter() {
        isRemote.value = true
        ApiClient.remoteCredential = RemoteAccess.pairing?.credential
    }
}
