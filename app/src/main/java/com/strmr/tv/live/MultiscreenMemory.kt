package com.strmr.tv.live

import android.content.Context
import com.strmr.tv.app.App
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last multiscreen, for Live › More › Resume multiscreen: its channels in screen order and
 * which one was audible. Device-local, per profile (the server's settings have no field for it,
 * so the Expo app's save never stuck).
 */
@Serializable
data class SavedMultiscreen(val channels: List<LiveChannel>, val audioIndex: Int = 0)

/** The pure rules behind [MultiscreenPrefs]: what is saved, its JSON, and when the warning shows. */
object MultiscreenMemory {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * What to save for `state`: the screens with a channel, in slot order, and the audible one
     * among them; null under two channels (an earlier selection then stays).
     */
    fun selection(state: MultiscreenSession.State): SavedMultiscreen? {
        val indices = state.slots.indices.filter { state.slots[it].channel != null }
        if (indices.size < 2) return null
        return SavedMultiscreen(indices.map { state.slots[it].channel!! }, maxOf(0, indices.indexOf(state.audioIndex)))
    }

    fun encode(saved: SavedMultiscreen): String = json.encodeToString(saved)

    /** A saved selection, or null if it doesn't parse or has under two channels. */
    fun decode(text: String?): SavedMultiscreen? {
        val saved = text?.let { runCatching { json.decodeFromString<SavedMultiscreen>(it) }.getOrNull() } ?: return null
        if (saved.channels.size < 2) return null
        return saved.copy(audioIndex = saved.audioIndex.coerceIn(0, saved.channels.lastIndex))
    }

    /** The stability warning before a third screen: once per device, whatever the answer was. */
    fun needsWarning(occupied: Int, shownBefore: Boolean) = occupied >= 2 && !shownBefore
}

/** [MultiscreenMemory] kept on this device. */
object MultiscreenPrefs {
    private val prefs by lazy { App.context.getSharedPreferences("strmr.multiscreen", Context.MODE_PRIVATE) }
    private const val KEY_WARNING = "warningShown"

    /** The "More screens, higher load" warning has been shown on this device. */
    val warningShown: Boolean get() = prefs.getBoolean(KEY_WARNING, false)

    fun markWarningShown() = prefs.edit().putBoolean(KEY_WARNING, true).apply()

    fun saved(profileId: String): SavedMultiscreen? = MultiscreenMemory.decode(prefs.getString("last.$profileId", null))

    fun save(profileId: String, selection: SavedMultiscreen) =
        prefs.edit().putString("last.$profileId", MultiscreenMemory.encode(selection)).apply()
}
