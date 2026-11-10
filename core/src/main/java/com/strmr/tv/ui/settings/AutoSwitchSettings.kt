package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * Settings › Playback › Auto Switch Sources: a movie or episode moves to another source by
 * itself when the server reports its source broken or too slow, or playback keeps stalling
 * (player/AutoSwitch). On by default.
 */
object AutoSwitchSettings {
    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY = "autoSwitchSources"

    var enabled by mutableStateOf(prefs.getBoolean(KEY, true))
        private set

    fun set(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(KEY, value).apply()
    }
}
