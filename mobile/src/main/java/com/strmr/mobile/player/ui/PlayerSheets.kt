package com.strmr.mobile.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import com.strmr.tv.player.releaseReportChoices
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.tv.player.MenuChoice
import com.strmr.tv.player.PlayerController
import com.strmr.tv.player.SourceSwitchCandidates
import com.strmr.tv.player.speedChoices
import com.strmr.tv.player.subtitleStyleChoices
import com.strmr.tv.player.trackMenuChoices

/** The player's bottom sheets. */
enum class PlayerSheet { AUDIO, SUBTITLES, SUBTITLE_STYLE, SUBTITLE_SEARCH, TRANSLATE, SETTINGS, SPEED, ASPECT, ORIENTATION, SOURCES, INFO, REPORT }

/**
 * The open sheet as a Material bottom sheet; moving from one to another (Subtitles › Style)
 * swaps the content in the same sheet. The menus' rows come from core, shared with the TV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheetHost(
    controller: PlayerController,
    ui: PlayerUiState,
    tablet: Boolean,
    aspectMode: AspectMode,
    onAspectMode: (AspectMode) -> Unit,
) {
    val sheet = ui.sheet ?: return
    val live = controller.session.isLive
    val done = { ui.sheet = null }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = sheet != PlayerSheet.SOURCES)
    ModalBottomSheet(onDismissRequest = done, sheetState = state) {
        when (sheet) {
            PlayerSheet.AUDIO, PlayerSheet.SUBTITLES -> {
                val audio = sheet == PlayerSheet.AUDIO
                val tracks by controller.tracks.collectAsState()
                val external by controller.external.track.collectAsState()
                val externalOn by controller.external.selected.collectAsState()
                val translation by controller.online.serverTranslation.collectAsState()
                val choices = trackMenuChoices(
                    controller, audio, tracks, external, externalOn, translation, done,
                    onStyle = { ui.sheet = PlayerSheet.SUBTITLE_STYLE },
                    onSearch = { ui.sheet = PlayerSheet.SUBTITLE_SEARCH }.takeIf { !live },
                    onTranslate = { ui.sheet = PlayerSheet.TRANSLATE }.takeIf { !live },
                )
                ChoiceSheet(if (audio) "Audio" else "Subtitles", choices, if (audio) "No audio tracks" else "No subtitles")
            }
            PlayerSheet.SUBTITLE_STYLE -> ChoiceSheet("Subtitle Style", subtitleStyleChoices(), "")
            PlayerSheet.SUBTITLE_SEARCH -> SubtitleSearchSheet(controller, done)
            PlayerSheet.TRANSLATE -> TranslateSheet(controller, done)
            PlayerSheet.SPEED -> {
                val speed by controller.speed.collectAsState()
                ChoiceSheet("Playback Speed", speedChoices(controller, speed, done), "")
            }
            PlayerSheet.ASPECT -> ChoiceSheet(
                "Aspect Ratio",
                AspectMode.entries.map { m -> MenuChoice(m.title, m.detail, selected = m == aspectMode) { onAspectMode(m); done() } },
                "",
            )
            PlayerSheet.ORIENTATION -> OrientationSheet(done)
            PlayerSheet.SOURCES -> {
                val candidates = remember { SourceSwitchCandidates(controller) }
                LaunchedEffect(Unit) { candidates.load() }
                ChoiceSheet("Switch Source", candidates.choices(done), candidates.emptyText)
            }
            PlayerSheet.REPORT -> {
                val flow = remember(controller) { controller.openReleaseReports() }
                val report by flow.state.collectAsState()
                LaunchedEffect(flow) { flow.load() }
                DisposableEffect(flow) { onDispose { controller.closeReleaseReports() } }
                ChoiceSheet("Report / change source", releaseReportChoices(flow, report, done), "Loading selected release...")
            }
            PlayerSheet.SETTINGS -> SettingsSheet(controller, ui, tablet, aspectMode)
            PlayerSheet.INFO -> InfoSheet(controller)
        }
    }
}

/**
 * A titled list of choices (the TV's popover menus as a sheet): the current one checked, headed
 * groups for sectioned rows, and the empty text when there's nothing to choose.
 */
@Composable
fun ChoiceSheet(title: String, choices: List<MenuChoice>, empty: String) {
    val listState = rememberLazyListState()
    val current = choices.indexOfFirst { it.selected }
    // A long list (Switch Source) has its current row off screen: start there (+1 for the title).
    LaunchedEffect(current >= 0) { if (current > 2) listState.scrollToItem(current) }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item { SheetTitle(title) }
        itemsIndexed(choices) { index, choice ->
            if (choice.section != null && choice.section != choices.getOrNull(index - 1)?.section) {
                Text(
                    choice.section!!, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = if (index == 0) 4.dp else 16.dp, bottom = 4.dp),
                )
            }
            ListItem(
                headlineContent = { Text(choice.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                supportingContent = choice.detail?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                leadingContent = {
                    Icon(
                        Icons.Filled.Check, contentDescription = if (choice.selected) "Selected" else null,
                        tint = if (choice.selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().clickable(onClick = choice.onClick),
            )
        }
        if (choices.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SheetTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
}
