package com.strmr.tv.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * Settings › Playback's track choices on this device: Subtitles (off / forced-only / on),
 * Subtitle Translation, Subtitle Language and Audio Language. Each is null for Default, which
 * follows the server (the profile's playback settings, `/api/settings` for translation);
 * a value overrides it here. The player picks tracks itself when an override differs from
 * the server's choice ([ClientTrackSelector]) and the online subtitle search uses the
 * effective values.
 */
object TrackOverrides {
    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val MODE = "subtitleMode"
    private const val TRANSLATION = "subtitleTranslation"
    private const val SUBTITLE_LANGUAGE = "subtitleLanguage"
    private const val AUDIO_LANGUAGE = "audioLanguage"

    val modes = listOf("off", "forced-only", "on")

    var subtitleMode by mutableStateOf(string(MODE))
        private set
    /** Only ever narrows the server's switch: Off here turns translation off, On can't turn it on. */
    var translation by mutableStateOf(if (prefs.contains(TRANSLATION)) prefs.getBoolean(TRANSLATION, true) else null)
        private set
    /** A 3-letter setting code ("eng"), as the profile stores it. */
    var subtitleLanguage by mutableStateOf(string(SUBTITLE_LANGUAGE))
        private set
    var audioLanguage by mutableStateOf(string(AUDIO_LANGUAGE))
        private set

    /** The server's values, shown as "Default (…)"; null until read (or unset on the server). */
    var serverSubtitleMode by mutableStateOf<String?>(null)
    var serverTranslation by mutableStateOf<Boolean?>(null)
    var serverSubtitleLanguage by mutableStateOf<String?>(null)
    var serverAudioLanguage by mutableStateOf<String?>(null)

    fun chooseSubtitleMode(value: String?) { subtitleMode = value; putString(MODE, value) }
    fun chooseSubtitleLanguage(value: String?) { subtitleLanguage = value; putString(SUBTITLE_LANGUAGE, value) }
    fun chooseAudioLanguage(value: String?) { audioLanguage = value; putString(AUDIO_LANGUAGE, value) }
    fun chooseTranslation(value: Boolean?) {
        translation = value
        prefs.edit().apply { if (value == null) remove(TRANSLATION) else putBoolean(TRANSLATION, value) }.apply()
    }

    fun modeName(mode: String?): String = when (mode) {
        "off" -> "Off"
        "forced-only" -> "Forced Only"
        "on" -> "On"
        else -> "Not set"
    }

    fun languageName(code: String?): String = code?.let { SubtitleLanguages.displayName(it) } ?: "Not set"

    private fun string(key: String): String? = prefs.getString(key, null)?.ifEmpty { null }

    private fun putString(key: String, value: String?) =
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
}
