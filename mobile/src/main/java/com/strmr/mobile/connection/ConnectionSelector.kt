package com.strmr.mobile.connection

import android.content.Context
import com.strmr.tv.app.App
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.NetworkRoutes
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.remote.RemoteAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl

/**
 * Chooses how the phone reaches the server (the iPhone app's `MobileConnectionSelector`,
 * following the Expo app):
 * - Paired over iroh with a home network configured: the home address only once it proves to be
 *   the paired server ([IdentityProbe]); the Wi-Fi name is just a hint ([RouteRules.shouldProbe]).
 *   Otherwise the tunnel, which is sticky: never an unverified LAN address.
 * - A typed server with a home network configured: the home or away address by Wi-Fi name
 *   ([RouteRules.direct]).
 * Main thread only.
 */
object ConnectionSelector {
    data class AutomaticHome(val url: HttpUrl, val ssid: String, val serverId: String)

    private val _settings = MutableStateFlow(NetworkSettings.cached)
    val settings: StateFlow<NetworkSettings?> = _settings
    /** The last Wi-Fi name read (null: unreadable or off Wi-Fi). */
    val lastSsid = MutableStateFlow<String?>(null)
    /** Whether the last check found the phone on its home network (null: couldn't tell). */
    val isHomeNetwork = MutableStateFlow<Boolean?>(null)
    val lastChecked = MutableStateFlow<Long?>(null)

    private val scope = MainScope()
    private var running: Deferred<Unit>? = null
    private val prefs by lazy { App.context.getSharedPreferences("strmr.connection", Context.MODE_PRIVATE) }

    /**
     * Debug builds: a paired phone stays on the tunnel even on the home Wi-Fi, so remote access
     * (playback over iroh included) can be tried at home, as the iPhone's debug network fault.
     */
    var stayRemote: Boolean
        get() = com.strmr.mobile.BuildConfig.DEBUG && stayRemoteState.value
        set(value) {
            stayRemoteState.value = value
            prefs.edit().putBoolean("debug.stayRemote", value).apply()
        }
    private val stayRemoteState by lazy { androidx.compose.runtime.mutableStateOf(prefs.getBoolean("debug.stayRemote", false)) }

    /** A home network that can take over from the tunnel: paired, with a home Wi-Fi and address. */
    val automaticHome: AutomaticHome?
        get() {
            val s = _settings.value ?: return null
            val url = s.homeUrl ?: return null
            if (s.homeWifiSsid.isEmpty()) return null
            val id = RemoteAccess.pairing?.invite?.let(RouteRules::serverId) ?: return null
            return AutomaticHome(url, s.homeWifiSsid, id)
        }

    /** Re-reads the network settings (once signed in) and re-selects when they changed. */
    suspend fun refreshSettings() {
        if (AppSession.phase.value != AppSession.Phase.Ready) return
        val fetched = try {
            NetworkSettings.fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        NetworkSettings.cache(fetched)
        if (fetched == _settings.value) return
        _settings.value = fetched
        ReconnectJournal.record("network-settings-changed", mapOf("configured" to "${fetched?.isConfigured == true}"))
        select("settings changed")
    }

    /** Re-evaluates the route. Joins a run in progress, except that a network or settings change restarts it. */
    suspend fun select(reason: String) {
        running?.let { job ->
            if (reason == "network change" || reason == "settings changed") job.cancel() else return job.await()
        }
        val job = scope.async { run(reason) }
        running = job
        try {
            job.await()
        } finally {
            if (running === job) running = null
        }
    }

    private suspend fun run(reason: String) {
        if (NetworkMonitor.isDeviceOffline.value) return
        if (AppSession.serverUrl.value == null && !NetworkRoutes.isPaired) return
        // A playing title keeps its route; recovery may still move it.
        val mayMove = !PlaybackCoordinator.playerOpen || RouteRules.mayMoveWhilePlaying(reason)
        if (RemoteAccess.pairing != null) selectPaired(reason, mayMove) else if (mayMove) selectDirect(reason)
        lastChecked.value = System.currentTimeMillis()
    }

    private val onCellular get() = NetworkMonitor.path.value.iface == NetworkMonitor.Interface.CELLULAR

    private suspend fun selectPaired(reason: String, mayMove: Boolean) {
        val home = automaticHome.takeUnless { stayRemote }
        var useHome = false
        if (home != null) {
            val ssid = if (onCellular) null else WifiSsid.current()
            lastSsid.value = ssid
            if (RouteRules.shouldProbe(onCellular, ssid, home.ssid)) useHome = IdentityProbe.matches(home.url, home.serverId)
        }
        if (!mayMove) return
        isHomeNetwork.value = if (home == null) null else useHome
        val session = AppSession
        if (useHome && home != null) {
            if (NetworkRoutes.isHomeDirect.value && session.serverUrl.value == home.url) return
            NetworkRoutes.routeHomeDirect(home.url)
            ReconnectJournal.record("route-home-direct", mapOf("reason" to reason))
        } else if (NetworkRoutes.isHomeDirect.value || !session.isRemote.value || reason != "periodic") {
            try {
                NetworkRoutes.routeRemoteTunnel()
                ReconnectJournal.record("route-remote", mapOf("reason" to reason))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReconnectJournal.record("route-remote-failed", mapOf("reason" to reason, "error" to (e.message ?: "")))
            }
        }
    }

    private fun selectDirect(reason: String) {
        val s = _settings.value?.takeIf { it.isConfigured } ?: return
        // Asking is worth it only when the answer picks between two addresses.
        val ask = s.homeUrl != null && s.remoteUrl != null
        val ssid = if (onCellular) null else WifiSsid.current(ask)
        lastSsid.value = ssid
        val recent = if (ssid == null && !onCellular) {
            RouteRules.recentDetection(prefs.takeIf { it.contains("detectedHome") }?.getBoolean("detectedHome", false),
                                       prefs.getLong("detectedAt", 0), System.currentTimeMillis())
        } else null
        val choice = RouteRules.direct(s, onCellular, ssid, recent)
        isHomeNetwork.value = choice.isHome
        when {
            onCellular -> prefs.edit().remove("detectedHome").apply()
            ssid != null -> prefs.edit().putBoolean("detectedHome", choice.isHome == true).putLong("detectedAt", System.currentTimeMillis()).apply()
        }
        val target = choice.target ?: return
        if (target == AppSession.serverUrl.value) return
        NetworkRoutes.routeNetworkUrl(target)
        ReconnectJournal.record(if (choice.isHome == true) "route-home-network" else "route-away-network", mapOf("reason" to reason))
    }
}
