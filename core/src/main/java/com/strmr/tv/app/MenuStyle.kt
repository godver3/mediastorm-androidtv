package com.strmr.tv.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Settings › Menu › Menu Style: the top tab bar (the default) or a side menu, Expo's TV drawer.
 * Device only: the server has no such setting (the Expo TV app always uses its drawer).
 * "Show Rail When Closed" follows the profile's `hideTvDrawerRail` unless this device chooses.
 */
object MenuStyle {
    private const val LOG = "Menu"
    private val prefs by lazy { App.context.getSharedPreferences("strmr.menuStyle", Context.MODE_PRIVATE) }

    /** True for the side menu. */
    var side by mutableStateOf(prefs.getBoolean("side", false))
        private set

    /** This device's rail choice; null follows the profile. */
    var railOverride by mutableStateOf(if (prefs.contains("showRail")) prefs.getBoolean("showRail", true) else null)
        private set

    /** The profile's (or its device override's) `hideTvDrawerRail`, as last loaded by [MenuTabs.refresh]. */
    var profileHidesRail by mutableStateOf(false)
        internal set

    val showsRail get() = railOverride ?: !profileHidesRail

    fun chooseSide(value: Boolean) {
        side = value
        prefs.edit().putBoolean("side", value).apply()
        AppLog.i(LOG, "style ${if (value) "side" else "top"} (chosen)")
    }

    fun chooseRail(value: Boolean?) {
        railOverride = value
        prefs.edit().apply { if (value == null) remove("showRail") else putBoolean("showRail", value) }.apply()
        AppLog.i(LOG, "rail ${value ?: "default"} (shows: $showsRail)")
    }
}
