package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * Settings › Appearance › Show Clock: the current time at the top right of Home and of the
 * player's chrome, on this device. On by default.
 */
object ClockSettings {
    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY = "showClock"

    var enabled by mutableStateOf(prefs.getBoolean(KEY, true))
        private set

    fun set(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(KEY, value).apply()
    }
}
