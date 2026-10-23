package com.strmr.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Translate… (the Expo track menu's translate mode): pick the subtitle track to translate
 * (the stream's text tracks, labelled as in the Subtitles menu), then the language to
 * translate it to (the Search Online languages). Back from the languages returns to the
 * tracks. Playback pauses while it is open, as for Search Online.
 */
@Composable
fun TranslateMenu(controller: PlayerController, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val online = controller.online
    val tracks by controller.tracks.collectAsState()
    var source by remember { mutableStateOf<PlayerTrack?>(null) }
    var languages by remember { mutableStateOf<List<SubtitleLanguages.Option>>(emptyList()) }

    DisposableEffect(Unit) {
        val wasPlaying = controller.player.isPlaying
        if (wasPlaying) controller.player.pause()
        onDispose { if (wasPlaying) controller.player.play() }
    }
    LaunchedEffect(Unit) { languages = online.searchLanguages().first }
    // Back from the languages: the tracks again (the chrome's handler closes the menu from the tracks).
    BackHandler(enabled = source != null) { source = null }

    val picked = source
    key(picked == null, languages.isEmpty()) {
        if (picked == null) {
            val choices = online.translatableTracks(tracks).map { track ->
                val f = track.format
                MenuChoice(f.menuTitle("Track ${track.typeIndex + 1}"), f.subtitleDetail(), selected = false) { source = track }
            }
            ChoiceMenu("Translate From", choices, "No text subtitles to translate", modifier)
        } else {
            val choices = languages.map { option ->
                MenuChoice(option.name, selected = false) {
                    online.translate(picked, option.code)
                    onDone()
                }
            }
            ChoiceMenu("Translate To", choices, "", modifier)
        }
    }
}
