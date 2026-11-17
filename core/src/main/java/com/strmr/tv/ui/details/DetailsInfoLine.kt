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
import com.strmr.tv.api.formatRuntime
import com.strmr.tv.player.WarmPrequeue
import com.strmr.tv.player.releaseTitle

/**
 * The optional line under the details actions: FILENAME · RUNTIME · AUDIO and SUB TRACK, each
 * segment behind its own profile setting ([DetailsInfoSettings]).
 */
data class DetailsInfoLine(
    /** The prequeue is still resolving: a spinner and "Finding a source…" in the filename's place. */
    val findingSource: Boolean,
    /** The prequeued release's name, truncated on its own. */
    val filename: String?,
    /** Runtime and tracks, already joined. */
    val rest: String?,
) {
    companion object {
        /** Null when every enabled segment is empty, so the line is hidden. */
        fun build(
            settings: DetailsInfoSettings,
            resolving: Boolean,
            filename: String?,
            runtime: String?,
            tracks: String?,
        ): DetailsInfoLine? {
            val finding = settings.showFilename && resolving
            val name = filename?.takeIf { settings.showFilename && !resolving }?.ifBlank { null }
            val rest = listOfNotNull(runtime?.takeIf { settings.showRuntime }, tracks?.takeIf { settings.showTracks && !resolving })
                .filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }
            if (!finding && name == null && rest == null) return null
            return DetailsInfoLine(finding, name, rest)
        }

        /** The page's line from its model and warm prequeue. */
        fun of(settings: DetailsInfoSettings, model: DetailsModel, tracks: PrequeueTracks, warm: WarmPrequeue?): DetailsInfoLine? {
            if (!settings.any) return null
            // Null when Play uses a library copy or the prequeue is unavailable.
            val shown = tracks.visible(warm)
            val status = shown?.tracks
            val resolving = shown != null && status == null
            // Episodes only: a movie's runtime is already in the metadata line.
            val minutes = if (model.target.isMovie) null else model.primaryEpisode?.runtimeMinutes
            return build(
                settings,
                resolving = resolving,
                filename = status?.releaseTitle,
                runtime = formatRuntime(minutes),
                tracks = status?.let { tracks.caption(it) },
            )
        }
    }
}

/**
 * The info line's settings: this device's overrides (Settings › Details Page; absent follows the
 * server), else the server's values, read when a details page or Settings opens. Off until read.
 */
object DetailsInfo {
    enum class Option(val key: String, val title: String, val detail: String) {
        Filename("detailsInfoFilename", "Show Source Filename", "The release Play will use, under the buttons"),
        Runtime("detailsInfoRuntime", "Show Episode Runtime", "The selected episode's runtime, under the buttons"),
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
        Option.Runtime -> server.showRuntime
        Option.Tracks -> server.showTracks
    }

    private fun value(option: Option) = overrides[option] ?: serverValue(option) ?: false

    /** What details pages show. */
    val settings: DetailsInfoSettings
        get() = DetailsInfoSettings(value(Option.Filename), value(Option.Runtime), value(Option.Tracks))

    suspend fun refresh() {
        server = ApiClient.detailsInfoServer()
    }
}
