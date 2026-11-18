package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * Settings › Playback › Match Frame Rate: this device's override of `matchFrameRate` (null
 * follows the server: the device's admin setting, else the profile's, off by default). When
 * on, the TV switches its refresh rate to the video's frame rate ([com.strmr.tv.player.FrameRateMatcher]).
 */
object MatchFrameRateSettings {
    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY = "matchFrameRate"

    /** On / off on this device; null for Default. */
    var override by mutableStateOf(read())
        private set

    /** The server's value, shown as "Default (…)"; null until read. */
    var serverValue by mutableStateOf<Boolean?>(null)

    fun set(value: Boolean?) {
        override = value
        prefs.edit().apply { if (value == null) remove(KEY) else putBoolean(KEY, value) }.apply()
    }

    /** Whether the player matches the frame rate, given the server's value. */
    fun enabled(server: Boolean): Boolean {
        serverValue = server
        return override ?: server
    }

    private fun read(): Boolean? = if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else null
}
