package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.EpisodePrivacySettings
import com.strmr.tv.api.episodePrivacy
import com.strmr.tv.app.App

/**
 * Settings › Spoilers: this device's overrides of the profile's `blurUnwatchedEpisode*`
 * settings (null follows the server: the device's admin setting, else the profile's), as the
 * other device settings override theirs. Details pages blur unwatched episodes' stills and
 * overviews with them (ui/details/EpisodePrivacy).
 */
object SpoilerSettings {
    enum class Option(val key: String, val title: String, val detail: String) {
        Thumbnails("blurUnwatchedEpisodeThumbnails", "Blur Unwatched Thumbnails", "Episode stills you haven't watched yet"),
        ThumbnailsIncludeNext("blurUnwatchedEpisodeThumbnailsIncludeCurrent", "Include Next Episode", "The still of the episode up next too"),
        Overviews("blurUnwatchedEpisodeOverviews", "Blur Unwatched Overviews", "Episode descriptions you haven't watched yet"),
        OverviewsIncludeNext("blurUnwatchedEpisodeOverviewsIncludeCurrent", "Include Next Episode", "The description of the episode up next too"),
    }

    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }

    /** On / off on this device; absent for Default. */
    private val overrides = mutableStateMapOf<Option, Boolean>().apply {
        Option.entries.forEach { if (prefs.contains(it.key)) put(it, prefs.getBoolean(it.key, false)) }
    }

    /** The server's values, shown as "Default (…)"; null until read. */
    var server by mutableStateOf<EpisodePrivacySettings?>(null)
        private set

    fun override(option: Option): Boolean? = overrides[option]

    fun set(option: Option, value: Boolean?) {
        if (value == null) overrides.remove(option) else overrides[option] = value
        prefs.edit().apply { if (value == null) remove(option.key) else putBoolean(option.key, value) }.apply()
    }

    fun serverValue(option: Option): Boolean? = server?.let {
        when (option) {
            Option.Thumbnails -> it.blurThumbnails
            Option.ThumbnailsIncludeNext -> it.blurThumbnailsIncludeCurrent
            Option.Overviews -> it.blurOverviews
            Option.OverviewsIncludeNext -> it.blurOverviewsIncludeCurrent
        }
    }

    /** Until the server answers, its defaults (blur on, the next episode spared), so nothing flashes unblurred. */
    private fun value(option: Option): Boolean =
        overrides[option] ?: serverValue(option) ?: (option == Option.Thumbnails || option == Option.Overviews)

    /** What details pages blur: this device's choices over the server's. */
    val effective: EpisodePrivacySettings
        get() = EpisodePrivacySettings(
            blurThumbnails = value(Option.Thumbnails),
            blurThumbnailsIncludeCurrent = value(Option.ThumbnailsIncludeNext),
            blurOverviews = value(Option.Overviews),
            blurOverviewsIncludeCurrent = value(Option.OverviewsIncludeNext),
        )

    suspend fun refresh() {
        server = ApiClient.episodePrivacy()
    }
}

/** Settings › Spoilers: each Default (the server's value, shown) or this device's own choice. */
fun LazyListScope.spoilerSettingRows() {
    SpoilerSettings.Option.entries.forEach { option ->
        item(key = option.key) {
            ChoiceRow(
                option.title, option.detail,
                choices = listOf("Off", "On"),
                selected = SpoilerSettings.override(option)?.let { if (it) 1 else 0 },
                defaultName = SpoilerSettings.serverValue(option)?.let { if (it) "On" else "Off" },
            ) { i -> SpoilerSettings.set(option, i?.let { it == 1 }) }
        }
    }
}
