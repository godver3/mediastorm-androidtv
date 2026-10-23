package com.strmr.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** One row of a player menu; a new `section` starts a headed group. */
data class MenuChoice(
    val title: String,
    val detail: String? = null,
    val selected: Boolean,
    val section: String? = null,
    val onClick: () -> Unit,
)

/**
 * A player popover (the tvOS transport bar's menus): a titled, rounded glass list rising
 * above the transport's buttons, the current choice checked and focused.
 */
@Composable
fun ChoiceMenu(title: String, choices: List<MenuChoice>, empty: String, modifier: Modifier = Modifier, width: Dp = 420.dp) {
    val current = remember { FocusRequester() }
    val focusIndex = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        // A long list (Switch Source) has its current row off screen: bring it in first (+1 for the title).
        if (focusIndex > 0) listState.scrollToItem(focusIndex + 1)
        runCatching { current.requestFocus() }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.width(width).heightIn(max = 520.dp)
            .shadow(24.dp, MenuShape).background(Color(0xF2242426), MenuShape),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                 color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
        }
        itemsIndexed(choices) { index, choice ->
            if (choice.section != null && choice.section != choices.getOrNull(index - 1)?.section) {
                Text(choice.section, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.5f),
                     modifier = Modifier.padding(start = 12.dp, top = if (index == 0) 0.dp else 10.dp, bottom = 2.dp))
            }
            ListItem(
                selected = choice.selected,
                onClick = choice.onClick,
                headlineContent = { Text(choice.title) },
                supportingContent = choice.detail?.let { { Text(it) } },
                leadingContent = {
                    if (choice.selected) Icon(Icons.Filled.Check, contentDescription = "Selected") else Icon(Icons.Filled.Check, null, tint = Color.Transparent)
                },
                colors = ListItemDefaults.colors(
                    selectedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                    focusedSelectedContainerColor = Color.White,
                    focusedSelectedContentColor = Color.Black,
                ),
                shape = ListItemDefaults.shape(RoundedCornerShape(12.dp)),
                modifier = if (index == focusIndex) Modifier.focusRequester(current) else Modifier,
            )
        }
        if (choices.isEmpty()) {
            item { Text(empty, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(12.dp)) }
        }
    }
}

/**
 * The Audio or Subtitles menu: the stream's tracks (subtitles with Off and any downloaded or
 * translated track, then Search Online…, Translate… and Style). Subtitles are drawn in the device's
 * [SubtitleStyle].
 */
@Composable
fun TrackMenu(
    controller: PlayerController,
    audio: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onStyle: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
) {
    val tracks by controller.tracks.collectAsState()
    val external by controller.external.track.collectAsState()
    val externalOn by controller.external.selected.collectAsState()
    val translation by controller.online.serverTranslation.collectAsState()
    val list = tracks.ofType(if (audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT)
        .filter { it.supported && (controller.session.isLive || !TrackPreferences.isClosedCaption(it.format)) }
    val ext = external.takeIf { !audio }
    val choices = buildList {
        if (!audio && (list.isNotEmpty() || ext != null)) {
            add(MenuChoice("Off", selected = list.none { it.selected } && !(ext != null && externalOn)) { controller.subtitlesOff(); onDone() })
        }
        ext?.let { t ->
            add(MenuChoice(t.title, t.detail, selected = externalOn) { controller.selectExternal(); onDone() })
        }
        list.forEach { track ->
            val f = track.format
            add(MenuChoice(f.menuTitle("Track ${track.typeIndex + 1}"), if (audio) f.audioDetail() else f.subtitleDetail(),
                           track.selected) { controller.select(track); onDone() })
        }
        if (!audio && onSearch != null && controller.online.available) {
            add(MenuChoice("Search Online…", selected = false, onClick = onSearch))
        }
        // Only while the server allows translation (its switch is a kill switch).
        if (!audio && onTranslate != null && controller.online.available && translation == true) {
            add(MenuChoice("Translate…", selected = false, onClick = onTranslate))
        }
        if (!audio && onStyle != null && (list.isNotEmpty() || ext != null)) {
            add(MenuChoice("Style", SubtitleStyle.sizeName(SubtitleStyleStore.style.size), selected = false, onClick = onStyle))
        }
    }
    ChoiceMenu(if (audio) "Audio" else "Subtitles", choices, if (audio) "No audio tracks" else "No subtitles", modifier)
}

/**
 * The Subtitles menu's Style picks over the device's [SubtitleStyle] (size, box, position, as
 * the tvOS quick menu offers); each pick applies at once and the full set lives in Settings.
 */
@Composable
fun SubtitleStyleMenu(modifier: Modifier = Modifier) {
    val style = SubtitleStyleStore.style
    val choices = SubtitleStyle.sizes.map { v ->
        MenuChoice(SubtitleStyle.sizeName(v), selected = v == style.size, section = "Size") { SubtitleStyleStore.update { it.copy(size = v) } }
    } + SubtitleStyle.backgroundOpacities.map { v ->
        MenuChoice(SubtitleStyle.percent(v), selected = v == style.backgroundOpacity, section = "Background") {
            SubtitleStyleStore.update { it.copy(backgroundOpacity = v) }
        }
    } + SubtitleStyle.margins.map { v ->
        MenuChoice(SubtitleStyle.marginName(v), selected = v == style.bottomMargin, section = "Position") {
            SubtitleStyleStore.update { it.copy(bottomMargin = v) }
        }
    }
    ChoiceMenu("Subtitle Style", choices, "", modifier)
}

/** Playback speed, as the tvOS player's Settings menu offers it. */
@Composable
fun SpeedMenu(controller: PlayerController, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val speed by controller.speed.collectAsState()
    val choices = SPEEDS.map { s ->
        MenuChoice(if (s == 1f) "Normal" else "${s}×", selected = s == speed) { controller.setSpeed(s); onDone() }
    }
    ChoiceMenu("Playback Speed", choices, "", modifier)
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val MenuShape = RoundedCornerShape(20.dp)
