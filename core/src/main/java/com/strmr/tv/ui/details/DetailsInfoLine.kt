package com.strmr.tv.ui.details

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsInfoSettings
import com.strmr.tv.api.DetailsInfoServer
import com.strmr.tv.api.detailsInfoServer
import com.strmr.tv.app.App
import com.strmr.tv.player.WarmPrequeue
import com.strmr.tv.player.releaseTitle

/**
 * The optional lines under the details actions: the prequeued release's name, then the audio and
 * subtitle tracks Play will use, each behind its own profile setting ([DetailsInfoSettings]).
 */
data class DetailsInfoLine(
    /** The prequeue is still resolving: a spinner and "Finding a source…" in the filename's place. */
    val findingSource: Boolean,
    /** The prequeued release's name, truncated on its own. */
    val filename: String?,
    /** The tracks, on the next line. */
    val tracks: SelectedTracks?,
) {
    companion object {
        /** Null when every enabled part is empty, so the lines are hidden. */
        fun build(settings: DetailsInfoSettings, resolving: Boolean, filename: String?, tracks: SelectedTracks?): DetailsInfoLine? {
            val finding = settings.showFilename && resolving
            val name = filename?.takeIf { settings.showFilename && !resolving }?.ifBlank { null }
            val shownTracks = tracks?.takeIf { settings.showTracks && !resolving }
            if (!finding && name == null && shownTracks == null) return null
            return DetailsInfoLine(finding, name, shownTracks)
        }

        /** The page's lines from its warm prequeue. */
        fun of(settings: DetailsInfoSettings, tracks: PrequeueTracks, warm: WarmPrequeue?): DetailsInfoLine? {
            if (!settings.any) return null
            // Null when Play uses a library copy or the prequeue is unavailable.
            val shown = tracks.visible(warm)
            val status = shown?.tracks
            return build(settings, resolving = shown != null && status == null, filename = status?.releaseTitle,
                         tracks = status?.let { tracks.selected(it) })
        }
    }
}

/**
 * The info lines' settings: this device's overrides (Settings › Details Page; absent follows the
 * server), else the server's values, read when a details page or Settings opens. Off until read.
 */
object DetailsInfo {
    enum class Option(val key: String, val title: String, val detail: String) {
        Filename("detailsInfoFilename", "Show Source Filename", "The release Play will use, under the buttons"),
        Tracks("detailsInfoSelectedTracks", "Show Selected Tracks", "The audio and subtitle tracks Play will use"),
    }

    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }

    /** On / off on this device; absent for Default. */
    private val overrides = mutableStateMapOf<Option, Boolean>().apply {
        Option.entries.forEach { if (prefs.contains(it.key)) put(it, prefs.getBoolean(it.key, false)) }
    }

    /** The server's values, shown as "Default (…)". */
    var server by mutableStateOf(DetailsInfoServer())
        private set

    fun override(option: Option): Boolean? = overrides[option]

    fun set(option: Option, value: Boolean?) {
        if (value == null) overrides.remove(option) else overrides[option] = value
        prefs.edit().apply { if (value == null) remove(option.key) else putBoolean(option.key, value) }.apply()
    }

    fun serverValue(option: Option): Boolean? = when (option) {
        Option.Filename -> server.showFilename
        Option.Tracks -> server.showTracks
    }

    private fun value(option: Option) = overrides[option] ?: serverValue(option) ?: false

    /** What details pages show. */
    val settings: DetailsInfoSettings
        get() = DetailsInfoSettings(value(Option.Filename), value(Option.Tracks))

    suspend fun refresh() {
        server = ApiClient.detailsInfoServer()
    }
}
