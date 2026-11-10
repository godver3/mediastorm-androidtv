package com.strmr.tv.ui.settings

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.api.subtitleServerSettings
import com.strmr.tv.player.RegionalAudio
import com.strmr.tv.player.SubtitleLanguages
import com.strmr.tv.player.TrackOverrides

/** Reads the server's values the track rows show as "Default (…)". */
suspend fun loadTrackDefaults() {
    val playback = runCatching { ApiClient.playbackSettings() }.getOrNull()
    TrackOverrides.serverSubtitleMode = playback?.preferredSubtitleMode
    TrackOverrides.serverSubtitleLanguage = playback?.preferredSubtitleLanguage
    TrackOverrides.serverAudioLanguage = playback?.preferredAudioLanguage
    TrackOverrides.serverTranslation = ApiClient.subtitleServerSettings().translationEnabled
}

/** The language pickers' choices, shared by the TV and phone Settings. */
object LanguageChoices {
    /** The languages; with [regional], each regional dub ("French (Canada)") after its language. */
    fun options(regional: Boolean): List<SubtitleLanguages.Option> = SubtitleLanguages.options.flatMap { option ->
        val variants = if (!regional) emptyList() else RegionalAudio.options
            .filter { SubtitleLanguages.canonical(it.first) == option.settingCode }
            .map { (code, name) -> SubtitleLanguages.Option(option.code, code, name) }
        listOf(option) + variants
    }

    /** [value]'s place in [options]; -1 for a code outside them, null for none chosen. */
    fun indexOf(options: List<SubtitleLanguages.Option>, value: String?): Int? = value?.let { v ->
        options.indexOfFirst {
            SubtitleLanguages.canonical(it.settingCode) == SubtitleLanguages.canonical(v) && RegionalAudio.sameVariant(it.settingCode, v)
        }
    }
}
