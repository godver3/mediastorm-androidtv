package com.strmr.tv.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/** Settings › Updates › Automatic Updates, kept on this device. */
object UpdateSettings {
    enum class Mode(val label: String, val detail: String) {
        /** Download in the background; install when the app is left, or ask where the system must. */
        Auto("On", "Download new builds and install them when you leave the app"),
        /** Download in the background, then ask before installing. */
        Ask("Ask First", "Download new builds, then ask before installing"),
        Off("Off", "Only check from this screen"),
    }

    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY = "updateMode"

    var mode by mutableStateOf(read())
        private set

    fun set(value: Mode) {
        mode = value
        prefs.edit().putString(KEY, value.name).apply()
    }

    /** On → Ask First → Off → On. */
    fun cycle() = set(Mode.entries[(mode.ordinal + 1) % Mode.entries.size])

    private fun read(): Mode = prefs.getString(KEY, null)?.let { name -> Mode.entries.firstOrNull { it.name == name } } ?: Mode.Auto
}
