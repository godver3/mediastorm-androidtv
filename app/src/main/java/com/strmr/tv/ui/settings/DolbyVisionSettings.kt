package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * Settings › Playback › Dolby Vision Profile 7: rewrite Profile 7 video on this device (to
 * Profile 8.1, or the HDR10 base layer) so its decoders can play it. On by default; read when
 * a playback starts (player/dv7/Dv7Playback).
 */
object DolbyVisionSettings {
    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY = "dolbyVisionProfile7"

    var profile7Enabled by mutableStateOf(prefs.getBoolean(KEY, true))
        private set

    fun setProfile7(value: Boolean) {
        profile7Enabled = value
        prefs.edit().putBoolean(KEY, value).apply()
    }
}
