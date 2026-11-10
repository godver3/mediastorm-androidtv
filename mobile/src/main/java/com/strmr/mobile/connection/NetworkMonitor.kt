package com.strmr.mobile.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import com.strmr.tv.app.App
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The phone's network (the iPhone app's `MobileNetworkMonitor`, from the default network's
 * capabilities): whether there is one, which interface carries it and whether it is metered.
 * As on iPhone and in the Expo app, the device counts as offline only when it still has no
 * network two seconds later, so a Wi-Fi to cellular handover isn't an outage. From Android 12
 * the callback asks for location info, so the Wi-Fi's name ([wifiSsid]) is readable when the
 * app may see it. Main thread only.
 */
object NetworkMonitor {
    enum class Interface(val title: String) {
        WIFI("Wi-Fi"), CELLULAR("Cellular"), WIRED("Ethernet"), OTHER("Other"), NONE("None")
    }

    data class Path(
        val satisfied: Boolean = true,
        val iface: Interface = Interface.OTHER,
        /** Cellular or a hotspot: Wi-Fi Only downloads wait. */
        val metered: Boolean = false,
    )

    private val _path = MutableStateFlow(Path())
    val path: StateFlow<Path> = _path
    private val _offline = MutableStateFlow(false)
    /** Confirmed offline (no network for two seconds). */
    val isDeviceOffline: StateFlow<Boolean> = _offline
    /** The Wi-Fi's name as the system reported it (null: unreadable or not on Wi-Fi). */
    @Volatile var wifiSsid: String? = null
        private set
    /** Called on the main thread after the path changes (not for the first report). */
    var onChange: ((Path) -> Unit)? = null

    private val scope = MainScope()
    private var confirm: Job? = null
    private var started = false
    private var reported = false
    private val connectivity get() = App.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun start() {
        if (started) return
        started = true
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = changed(caps)
                override fun onLost(network: Network) = lost()
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = changed(caps)
                override fun onLost(network: Network) = lost()
            }
        }
        val current = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        if (current == null) apply(Path(false, Interface.NONE)) else apply(describe(current))
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
    }

    /** The path as the system reports it now (Connection settings' refresh). */
    fun refresh() {
        val caps = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        scope.launch { apply(caps?.let(::describe) ?: Path(false, Interface.NONE)) }
    }

    private fun changed(caps: NetworkCapabilities) {
        val path = describe(caps)
        wifiSsid = if (path.iface == Interface.WIFI) ssidOf(caps) ?: wifiSsid else null
        scope.launch { apply(path) }
    }

    private fun lost() {
        scope.launch {
            // Another network may already be the default (a handover).
            val caps = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
            apply(caps?.let(::describe) ?: Path(false, Interface.NONE))
        }
    }

    private fun apply(next: Path) {
        val first = !reported
        reported = true
        if (!first && next == _path.value) return
        _path.value = next
        ReconnectJournal.record("network-state", mapOf("connected" to "${next.satisfied}", "interface" to next.iface.name.lowercase(),
                                                       "metered" to "${next.metered}"))
        if (next.satisfied) {
            confirm?.cancel()
            confirm = null
            _offline.value = false
        } else if (first) {
            _offline.value = true
        } else if (confirm == null) {
            confirm = scope.launch {
                delay(2_000)
                confirm = null
                if (!_path.value.satisfied) {
                    _offline.value = true
                    ReconnectJournal.record("device-offline-confirmed")
                }
            }
        }
        if (!first) onChange?.invoke(next)
    }

    private fun describe(caps: NetworkCapabilities): Path {
        val iface = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Interface.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Interface.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Interface.WIRED
            else -> Interface.OTHER
        }
        return Path(
            satisfied = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            iface = iface,
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }

    private fun ssidOf(caps: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return WifiSsid.clean((caps.transportInfo as? WifiInfo)?.ssid)
    }
}
