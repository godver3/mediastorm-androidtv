package com.strmr.mobile.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.tv.api.SubtitleSearchResult
import com.strmr.tv.player.MenuChoice
import com.strmr.tv.player.OnlineSubtitles
import com.strmr.tv.player.PlayerController
import com.strmr.tv.player.PlayerTrack
import com.strmr.tv.player.SubtitleLanguages
import com.strmr.tv.player.translateSourceChoices
import kotlinx.coroutines.CancellationException

/** Pauses playback while a subtitle sheet is open (as the TV and Expo players), resuming after. */
@Composable
private fun PauseWhileOpen(controller: PlayerController) {
    DisposableEffect(Unit) {
        val wasPlaying = controller.player.isPlaying
        if (wasPlaying) controller.player.pause()
        onDispose { if (wasPlaying) controller.player.play() }
    }
}

/**
 * Search Online: chips for the profile's subtitle languages (opening on its own) over the
 * providers' results, closest release first, each with its provider, HI and download count.
 * Picking one downloads it as an "Online: <language>" track.
 */
@Composable
fun SubtitleSearchSheet(controller: PlayerController, onDone: () -> Unit) {
    val online = controller.online
    var languages by remember { mutableStateOf<List<SubtitleLanguages.Option>>(emptyList()) }
    var language by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SubtitleSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    PauseWhileOpen(controller)
    LaunchedEffect(Unit) {
        val (options, initial) = online.searchLanguages()
        languages = options
        language = initial
    }
    LaunchedEffect(language) {
        val lang = language ?: return@LaunchedEffect
        loading = true
        error = null
        results = emptyList()
        try {
            results = online.search(lang)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            online.log(OnlineSubtitles.SEARCH_TAG, "search_error", "language=$lang error=${e.message}")
            error = "Failed to search for subtitles"
        }
        loading = false
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetTitle("Search Subtitles")
        online.releaseName?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                 maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
            items(languages, key = { it.code }) { option ->
                FilterChip(selected = option.code == language, onClick = { language = option.code }, label = { Text(option.name) })
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 520.dp)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Message(error!!, Modifier.align(Alignment.Center))
                results.isEmpty() -> Message("No subtitles found", Modifier.align(Alignment.Center))
                else -> LazyColumn {
                    items(results, key = { "${it.provider}:${it.id}" }) { result ->
                        ResultRow(result) {
                            online.use(result, language ?: return@ResultRow)
                            onDone()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: SubtitleSearchResult, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(result.release.ifEmpty { "Unnamed release" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("%,d downloads".format(result.downloads)) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result.hearingImpaired) AssistChip(onClick = onClick, label = { Text("HI") })
                AssistChip(onClick = onClick, label = { Text(result.provider) })
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

/**
 * Translate…: the subtitle track to translate (the stream's text tracks), then the language to
 * translate it to (the Search Online languages).
 */
@Composable
fun TranslateSheet(controller: PlayerController, onDone: () -> Unit) {
    val online = controller.online
    val tracks by controller.tracks.collectAsState()
    var source by remember { mutableStateOf<PlayerTrack?>(null) }
    var languages by remember { mutableStateOf<List<SubtitleLanguages.Option>>(emptyList()) }
    PauseWhileOpen(controller)
    LaunchedEffect(Unit) { languages = online.searchLanguages().first }
    androidx.activity.compose.BackHandler(enabled = source != null) { source = null }
    val picked = source
    if (picked == null) {
        ChoiceSheet("Translate From", translateSourceChoices(controller, tracks) { source = it }, "No text subtitles to translate")
    } else {
        val choices = languages.map { option ->
            MenuChoice(option.name, selected = false) {
                online.translate(picked, option.code)
                onDone()
            }
        }
        ChoiceSheet("Translate To", choices, "Loading languages…")
    }
}
