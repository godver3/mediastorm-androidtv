package com.strmr.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.api.SubtitleSearchResult
import kotlinx.coroutines.CancellationException

/**
 * Search Online (the Expo player's subtitle search modal): a row of languages (the profile's
 * allowed ones, opening on its subtitle language) over the providers' results, closest
 * release first, each with its provider, HI and download count. Playback pauses while it is
 * open; picking a result downloads it as an "Online: <language>" track.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SubtitleSearchPanel(controller: PlayerController, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val online = controller.online
    var languages by remember { mutableStateOf<List<SubtitleLanguages.Option>>(emptyList()) }
    var language by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SubtitleSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val chip = remember { FocusRequester() }

    DisposableEffect(Unit) {
        val wasPlaying = controller.player.isPlaying
        if (wasPlaying) controller.player.pause()
        onDispose { if (wasPlaying) controller.player.play() }
    }
    LaunchedEffect(Unit) {
        val (options, initial) = online.searchLanguages()
        languages = options
        language = initial
    }
    LaunchedEffect(languages.isNotEmpty()) { if (languages.isNotEmpty()) runCatching { chip.requestFocus() } }
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

    Column(
        modifier.width(760.dp).fillMaxHeight().padding(vertical = 48.dp)
            .shadow(24.dp, PanelShape).background(Color(0xF2242426), PanelShape).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Search Subtitles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        online.releaseName?.let {
            Text(it, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium,
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(languages, key = { it.code }) { option ->
                val selected = option.code == language
                FilterChip(
                    selected = selected,
                    onClick = { language = option.code },
                    modifier = if (selected) Modifier.focusRequester(chip) else Modifier,
                ) { Text(option.name) }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White, strokeWidth = 3.dp)
                error != null -> Message(error!!, Modifier.align(Alignment.Center))
                results.isEmpty() -> Message("No subtitles found", Modifier.align(Alignment.Center))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        selected = false,
        onClick = onClick,
        headlineContent = { Text(result.release.ifEmpty { "Unnamed release" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("%,d downloads".format(result.downloads)) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result.hearingImpaired) Badge("HI")
                Badge(result.provider)
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        shape = ListItemDefaults.shape(RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun Badge(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(Color.Gray.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Text(text, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

/** The online subtitle status ("Searching for subtitles…"), top right, over the video and the chrome. */
@Composable
fun SubtitleStatusToast(controller: PlayerController, modifier: Modifier = Modifier) {
    val message by controller.online.status.collectAsState()
    var last by remember { mutableStateOf("") }
    message?.let { last = it }
    AnimatedVisibility(message != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        Text(
            last, style = MaterialTheme.typography.titleSmall, color = Color.White,
            modifier = Modifier.padding(top = 40.dp, end = 64.dp)
                .background(Color(0xE0242426), RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

private val PanelShape = RoundedCornerShape(20.dp)
