package com.strmr.tv.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.api.subtitleServerSettings
import com.strmr.tv.player.RegionalAudio
import com.strmr.tv.player.SubtitleLanguages
import com.strmr.tv.player.TrackOverrides
import com.strmr.tv.ui.PickerDialog

/** Reads the server's values the track rows show as "Default (…)". */
suspend fun loadTrackDefaults() {
    val playback = runCatching { ApiClient.playbackSettings() }.getOrNull()
    TrackOverrides.serverSubtitleMode = playback?.preferredSubtitleMode
    TrackOverrides.serverSubtitleLanguage = playback?.preferredSubtitleLanguage
    TrackOverrides.serverAudioLanguage = playback?.preferredAudioLanguage
    TrackOverrides.serverTranslation = ApiClient.subtitleServerSettings().translationEnabled
}

/**
 * Settings › Playback: Subtitles, Subtitle Translation, Subtitle Language and Audio
 * Language, each Default (the server's value, shown) or this device's own choice.
 */
fun LazyListScope.trackSettingRows() {
    item {
        val o = TrackOverrides
        val modes = TrackOverrides.modes
        ChoiceRow(
            "Subtitles", "When subtitles show",
            choices = modes.map { o.modeName(it) },
            selected = o.subtitleMode?.let { modes.indexOf(it) },
            defaultName = o.serverSubtitleMode?.let { o.modeName(it) },
        ) { i -> o.chooseSubtitleMode(i?.let { modes[it] }) }
    }
    item {
        val o = TrackOverrides
        val detail = "Translate English subtitles when none are in your language"
        if (o.serverTranslation == false) {
            // The server's switch is an admin kill switch: off there is off on every device.
            ListItem(
                selected = false, enabled = false, onClick = {},
                headlineContent = { Text("Subtitle Translation") },
                supportingContent = { Text(detail) },
                trailingContent = { Text("Off (disabled on server)") },
            )
        } else {
            ChoiceRow(
                "Subtitle Translation", detail,
                choices = listOf("Off", "On"),
                selected = o.translation?.let { if (it) 1 else 0 },
                defaultName = o.serverTranslation?.let { if (it) "On" else "Off" },
            ) { i -> o.chooseTranslation(i?.let { it == 1 }) }
        }
    }
    item {
        val o = TrackOverrides
        LanguageRow("Subtitle Language", o.subtitleLanguage, o.serverSubtitleLanguage, o::chooseSubtitleLanguage)
    }
    item {
        val o = TrackOverrides
        LanguageRow("Audio Language", o.audioLanguage, o.serverAudioLanguage, o::chooseAudioLanguage, regional = true)
    }
}

/** With `regional`, each regional dub ("French (Canada)") follows its language. */
@Composable
private fun LanguageRow(title: String, value: String?, server: String?, set: (String?) -> Unit, regional: Boolean = false) {
    val options = SubtitleLanguages.options.flatMap { option ->
        val variants = if (!regional) emptyList() else RegionalAudio.options
            .filter { SubtitleLanguages.canonical(it.first) == option.settingCode }
            .map { (code, name) -> SubtitleLanguages.Option(option.code, code, name) }
        listOf(option) + variants
    }
    val selected = value?.let { v ->
        options.indexOfFirst {
            SubtitleLanguages.canonical(it.settingCode) == SubtitleLanguages.canonical(v) && RegionalAudio.sameVariant(it.settingCode, v)
        }
    }
    ChoiceRow(
        title, null,
        choices = options.map { it.name },
        // An override outside the list still shows by name.
        selected = selected?.takeIf { it >= 0 },
        defaultName = server?.let { TrackOverrides.languageName(it) },
        currentName = value?.let { TrackOverrides.languageName(it) },
    ) { i -> set(i?.let { options[it].settingCode }) }
}

/**
 * A row whose picker offers "Default (server value)" and then `choices`; `selected` is the
 * index of the device's choice, null for Default.
 */
@Composable
private fun ChoiceRow(
    title: String,
    detail: String?,
    choices: List<String>,
    selected: Int?,
    defaultName: String?,
    currentName: String? = null,
    onPick: (Int?) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val defaultLabel = "Default" + (defaultName?.let { " ($it)" } ?: "")
    if (picking) {
        PickerDialog(title, listOf(defaultLabel) + choices, selected?.let { it + 1 } ?: 0,
                     onPick = { onPick(if (it == 0) null else it - 1) }, onDismiss = { picking = false })
    }
    ListItem(
        selected = false, onClick = { picking = true },
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = { Text(selected?.let { choices[it] } ?: currentName ?: defaultLabel) },
    )
}
