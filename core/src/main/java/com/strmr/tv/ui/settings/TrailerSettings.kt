package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * Settings › Playback › Auto-play Trailers: this device's override of `autoPlayTrailersTV`
 * (null follows the server: the device's admin setting, else the profile's), as the other
 * device settings override theirs.
 */
object TrailerSettings {
    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY = "autoPlayTrailers"

    /** On / off on this device; null for Default. */
    var override by mutableStateOf(read())
        private set

    /** The server's value, shown as "Default (…)"; null until read. */
    var serverValue by mutableStateOf<Boolean?>(null)

    fun set(value: Boolean?) {
        override = value
        prefs.edit().apply { if (value == null) remove(KEY) else putBoolean(KEY, value) }.apply()
    }

    /** Whether details pages play the trailer behind the header, given the server's value. */
    fun autoPlay(server: Boolean): Boolean {
        serverValue = server
        return override ?: server
    }

    private fun read(): Boolean? = if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else null
}
