package com.strmr.mobile.player

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * The phone player's device settings, kept across sessions: how it is oriented (the iPhone
 * app's Player Orientation, landscape until the viewer picks another) and whether audio keeps
 * playing with the app in the background (the Expo player's `playInBackground`). The player's
 * Settings sheet changes both; Settings › Playback can offer them too.
 */
object PlayerPrefs {
    enum class Orientation(val title: String) {
        LANDSCAPE("Landscape"),
        PORTRAIT("Portrait"),
        DEVICE("Follow Device Rotation"),
    }

    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val ORIENTATION_KEY = "phonePlayerOrientation"
    private const val BACKGROUND_KEY = "phonePlayerBackgroundAudio"

    private var orientationState by mutableStateOf(readOrientation())
    private var backgroundState by mutableStateOf(prefs.getBoolean(BACKGROUND_KEY, false))

    var orientation: Orientation
        get() = orientationState
        set(value) {
            orientationState = value
            prefs.edit().putString(ORIENTATION_KEY, value.name).apply()
        }

    var backgroundAudio: Boolean
        get() = backgroundState
        set(value) {
            backgroundState = value
            prefs.edit().putBoolean(BACKGROUND_KEY, value).apply()
        }

    private fun readOrientation(): Orientation =
        prefs.getString(ORIENTATION_KEY, null)?.let { runCatching { Orientation.valueOf(it) }.getOrNull() } ?: Orientation.LANDSCAPE
}

/**
 * The player activity's requested orientation: a tablet turns freely; a phone follows the
 * choice (Follow Device Rotation still honours the system's rotation lock, as iOS does).
 */
fun playerOrientation(tablet: Boolean, choice: PlayerPrefs.Orientation): Int = when {
    tablet -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    choice == PlayerPrefs.Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    choice == PlayerPrefs.Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
}
