package com.strmr.mobile.connection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.strmr.tv.app.App
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The current Wi-Fi's name, for telling home from away. Android shows it only to an app
 * with precise location access, with location turned on; otherwise (and off Wi-Fi) it is
 * null, which the selector treats as "unknown", as iPhone does. The app asks for the
 * permission only when a home Wi-Fi is configured: once per launch when switching between two
 * addresses depends on it, or from Connection settings ([WifiPermissionPrompt] shows the ask).
 */
object WifiSsid {
    /** A request for location access is waiting for the prompt to show it. */
    val wantsPermission = MutableStateFlow(false)
    private var askedThisLaunch = false

    val isAllowed: Boolean
        get() = ContextCompat.checkSelfPermission(App.context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    val locationOn: Boolean
        get() = runCatching {
            LocationManagerCompat.isLocationEnabled(App.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        }.getOrDefault(false)

    /** The SSID, asking for access first when [ask] and it wasn't asked this launch. */
    fun current(ask: Boolean = false): String? {
        if (!isAllowed) {
            if (ask && !askedThisLaunch) requestAccess()
            return null
        }
        if (!locationOn) return null
        NetworkMonitor.wifiSsid?.let { return it }
        @Suppress("DEPRECATION")
        val info = runCatching { (App.context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo }
            .getOrNull()
        return clean(info?.ssid)
    }

    fun requestAccess() {
        askedThisLaunch = true
        wantsPermission.value = true
    }

    /** Strips the quotes Android puts around a name; the redacted placeholder is null. */
    fun clean(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"") ?: return null
        return s.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
    }
}
