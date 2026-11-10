package com.strmr.mobile.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.strmr.mobile.player.PlayerPrefs
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.live.LiveHlsPreference
import com.strmr.tv.player.TrackOverrides
import com.strmr.tv.ui.settings.AutoSwitchSettings
import com.strmr.tv.ui.settings.DolbyVisionSettings
import com.strmr.tv.ui.settings.LanguageChoices
import com.strmr.tv.ui.settings.TrailerSettings
import com.strmr.tv.ui.settings.loadTrackDefaults

/**
 * Settings › Playback: the TV's track overrides (each Default, the server's value, or this
 * device's), audio format, the phone player's rotation and background playback, source
 * switching, trailers, Dolby Vision Profile 7 and the live HLS reset.
 */
@Composable
fun PlaybackSettingsPage() {
    val o = TrackOverrides
    LaunchedEffect(Unit) { runCatching { loadTrackDefaults() } }
    LaunchedEffect(Unit) { runCatching { TrailerSettings.serverValue = ApiClient.playbackSettings().autoPlayTrailers } }

    SettingsPage("Playback") {
        SettingsSection(
            "Subtitles",
            "Foreign Audio shows forced subtitles for audio in your language and full ones for any other. Translation translates English subtitles when none are in your language.",
        ) {
            val modes = TrackOverrides.modes
            OverrideRow(
                "Subtitles", modes.map { o.modeName(it) }, o.subtitleMode?.let { modes.indexOf(it) }?.takeIf { it >= 0 },
                o.serverSubtitleMode?.let { o.modeName(it) },
            ) { i -> o.chooseSubtitleMode(i?.let { modes[it] }) }
            LanguageRow("Subtitle Language", o.subtitleLanguage, o.serverSubtitleLanguage, regional = false, o::chooseSubtitleLanguage)
            if (o.serverTranslation == false) {
                // The server's switch is the admin's: a device can turn translation off, never on.
                ValueRow("Subtitle Translation", "Off (disabled on server)")
            } else {
                OverrideRow(
                    "Subtitle Translation", listOf("Off", "On"), o.translation?.let { if (it) 1 else 0 },
                    o.serverTranslation?.let(::onOff),
                ) { i -> o.chooseTranslation(i?.let { it == 1 }) }
            }
            PageRow("Subtitle Style", SettingsKeys.SUBTITLES)
        }
        SettingsSection("Audio", "Audio Format picks which track starts when a source has several in your language.") {
            LanguageRow("Audio Language", o.audioLanguage, o.serverAudioLanguage, regional = true, o::chooseAudioLanguage)
            PickerRow("Audio Format", listOf("Compatible (E-AC-3)", "Lossless (TrueHD)"), if (o.losslessAudio) 1 else 0) {
                o.chooseLosslessAudio(it == 1)
            }
        }
        SettingsSection("Player", "Follow Device Rotation starts the player the way the phone is held and turns with it. Tablets always turn freely.") {
            val orientations = PlayerPrefs.Orientation.entries
            PickerRow("Player Orientation", orientations.map { it.title }, orientations.indexOf(PlayerPrefs.orientation)) {
                PlayerPrefs.orientation = orientations[it]
            }
            SwitchRow(
                "Background Playback", PlayerPrefs.backgroundAudio, "Keep the sound playing when you leave the app",
            ) { PlayerPrefs.backgroundAudio = it }
        }
        SettingsSection("Sources") {
            SwitchRow(
                "Auto Switch Sources", AutoSwitchSettings.enabled, "Move to another source when the current one fails or keeps stalling",
            ) { AutoSwitchSettings.set(it) }
            OverrideRow(
                "Auto-play Trailers", listOf("Off", "On"), TrailerSettings.override?.let { if (it) 1 else 0 },
                TrailerSettings.serverValue?.let(::onOff), "Behind the details page's header",
            ) { i -> TrailerSettings.set(i?.let { it == 1 }) }
            SwitchRow(
                "Dolby Vision Profile 7", DolbyVisionSettings.profile7Enabled, "Convert UHD Blu-ray Dolby Vision so this device can play it",
            ) { DolbyVisionSettings.setProfile7(it) }
        }
        SettingsSection(
            "Live TV Playback Recovery",
            "After a successful HLS recovery, this device remembers HLS for that source. Reset them to try their own stream format again.",
        ) {
            var result by remember { mutableStateOf<String?>(null) }
            ValueRow("Try Direct Again", result.orEmpty()) {
                result = when (val count = LiveHlsPreference.resetAll()) {
                    0 -> "Nothing to reset"
                    1 -> "Reset 1 source"
                    else -> "Reset $count sources"
                }
            }
        }
    }
}

/** A language override; with [regional], each regional dub ("French (Canada)") follows its language. */
@Composable
private fun LanguageRow(title: String, value: String?, server: String?, regional: Boolean, set: (String?) -> Unit) {
    val options = remember(regional) { LanguageChoices.options(regional) }
    OverrideRow(
        title, options.map { it.name },
        // An override outside the list still shows by name.
        LanguageChoices.indexOf(options, value)?.takeIf { it >= 0 },
        server?.let { TrackOverrides.languageName(it) },
        currentName = value?.let { TrackOverrides.languageName(it) },
    ) { i -> set(i?.let { options[it].settingCode }) }
}
