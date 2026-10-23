package com.strmr.tv.app

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.checkHealth
import com.strmr.tv.api.checkVersion
import com.strmr.tv.api.logout
import com.strmr.tv.api.publicBranding
import com.strmr.tv.api.serverVersion
import com.strmr.tv.auth.AuthSession
import com.strmr.tv.auth.DiscoveredServer
import com.strmr.tv.auth.ServerAddress
import com.strmr.tv.launcher.LauncherSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** [AppSession]'s server: connecting to a typed or discovered address, and switching to it. Main thread only. */
internal object ServerSetup {
    private val api = ApiClient

    /** Validates the typed address and moves on to sign-in. */
    suspend fun connect(input: String) {
        val url = ServerAddress.normalize(input)
            ?: throw ApiError.NotReady("Enter a server address, like 192.168.1.10 or media.example.com.")
        api.checkHealth(url)
        if (RemoteSession.isRemote.value) RemoteSession.leave()
        if (url != AppSession.serverUrl.value) {
            LauncherSync.clear("server changed")
            api.logout()
            SessionPrefs.forgetProfiles()
        }
        AppSession.useServer(url)
        SessionPrefs.saveServer(url)
        if (api.session == null) AppSession.moveTo(AppSession.Phase.Login) else AppSession.resumeSession()
    }

    /** A server found on the LAN: confirm with `/api/version`, then continue as if typed. */
    suspend fun connect(discovered: DiscoveredServer) {
        api.checkVersion(discovered.baseUrl)
        connect(discovered.baseUrl.toString())
    }

    /** Points the API at `url` with its stored session; its version and branding load in `scope`. */
    fun use(url: HttpUrl, scope: CoroutineScope) = with(AppSession) {
        serverUrl.value = url
        api.baseUrl = url
        api.session = AuthSession.load()
        brandLogoUrl.value = null
        scope.launch { serverVersion.value = api.serverVersion() }
        scope.launch {
            val path = api.publicBranding()?.logoPath ?: return@launch
            if (serverUrl.value == url) brandLogoUrl.value = url.resolve(path)?.toString()
        }
    }

    /** No server: nothing of the last one stays (a Keystore token can outlive a data clear). */
    suspend fun forgetEverything() {
        LauncherSync.clear("forgot the server")
        RemoteSession.leave()
        SessionPrefs.forgetAll()
        api.forgetSession()
    }
}
