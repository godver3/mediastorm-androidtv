package com.strmr.mobile.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * The phone player's own device settings (Settings › Playback), read by the player when it
 * opens and observable as Compose state while it plays.
 */
object MobilePlayerPrefs {
    private val prefs by lazy { App.context.getSharedPreferences("strmr.mobile", Context.MODE_PRIVATE) }
    private const val KEY_FOLLOW_ROTATION = "player.followRotation"
    private const val KEY_BACKGROUND_AUDIO = "player.backgroundAudio"

    private var rotation by mutableStateOf(prefs.getBoolean(KEY_FOLLOW_ROTATION, false))
    private var background by mutableStateOf(prefs.getBoolean(KEY_BACKGROUND_AUDIO, false))

    /**
     * Follow Device Rotation: the player starts the way the phone is held and turns with it.
     * Off (the default, as on iPhone), it stays in landscape.
     */
    var followRotation: Boolean
        get() = rotation
        set(value) {
            rotation = value
            prefs.edit().putBoolean(KEY_FOLLOW_ROTATION, value).apply()
        }

    /** Background Playback: the sound keeps playing when the app leaves the screen. Off by default. */
    var backgroundAudio: Boolean
        get() = background
        set(value) {
            background = value
            prefs.edit().putBoolean(KEY_BACKGROUND_AUDIO, value).apply()
        }
}
