package com.strmr.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.text.Cue
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.player.SubtitleStyle
import com.strmr.tv.player.SubtitleStyle.Overrides
import com.strmr.tv.player.SubtitleStyleStore
import com.strmr.tv.player.SubtitleOverlay
import com.strmr.tv.ui.pageBackground
import com.strmr.tv.ui.PickerDialog

/** One open picker: its title, choices (Default first) and what a pick writes. */
private class Picking(val title: String, val options: List<String>, val selected: Int, val pick: (Int) -> Unit)

/**
 * Settings › Subtitle Style: the device's [SubtitleStyle], each setting over the server's
 * ("Default" follows it), with a live preview of a 16:9 frame drawn by the player's own view.
 */
@Composable
fun SubtitleSettings() {
    val store = SubtitleStyleStore
    val style = store.style
    val server = store.server
    val o = store.overrides
    var picking by remember { mutableStateOf<Picking?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LaunchedEffect(Unit) { store.adopt(ApiClient.playbackSettings().subtitleStyle) }

    /** A row whose picker offers Default (the server's value) and `choices`. */
    @Composable
    fun <T> Choice(
        title: String, override: T?, serverValue: T, choices: List<T>, name: (T) -> String,
        modifier: Modifier = Modifier, write: (Overrides, T?) -> Overrides,
    ) {
        val all = (choices + listOfNotNull(override, serverValue)).distinct()
        ListItem(
            selected = false,
            onClick = {
                picking = Picking(title, listOf("Default (${name(serverValue)})") + all.map(name),
                                  override?.let { all.indexOf(it) + 1 } ?: 0) { i ->
                    store.update { write(it, if (i == 0) null else all[i - 1]) }
                }
            },
            headlineContent = { Text(title) },
            supportingContent = { Text(override?.let(name) ?: "Default (${name(serverValue)})") },
            modifier = modifier,
        )
    }

    val colors = SubtitleStyle.palette.map { it.second }
    Box(Modifier.fillMaxSize().pageBackground()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SubtitlePreview(style, Modifier.weight(1f))
            LazyColumn(
                Modifier.width(420.dp).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Text("Subtitle Style", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
                item { Header("Text") }
                item {
                    Choice("Size", o.size, server.size, SubtitleStyle.sizes, SubtitleStyle::sizeName, Modifier.focusRequester(first)) { s, v ->
                        s.copy(size = v)
                    }
                }
                item { Choice("Colour", o.textColor, server.textColor, colors, SubtitleStyle::colorName) { s, v -> s.copy(textColor = v) } }
                item {
                    Choice("Opacity", o.textOpacity, server.textOpacity, SubtitleStyle.opacities, SubtitleStyle::percent) { s, v ->
                        s.copy(textOpacity = v)
                    }
                }
                item { Choice("Bold", o.bold, server.bold, listOf(true, false), ::onOff) { s, v -> s.copy(bold = v) } }
                item { Header("Edge") }
                item { Choice("Style", o.edge, server.edge, SubtitleStyle.Edge.entries, { it.label }) { s, v -> s.copy(edge = v) } }
                if (style.edge == SubtitleStyle.Edge.OUTLINE) {
                    item {
                        Choice("Outline Colour", o.outlineColor, server.outlineColor, colors, SubtitleStyle::colorName) { s, v ->
                            s.copy(outlineColor = v)
                        }
                    }
                    item {
                        Choice("Outline Width", o.outlineWeight, server.outlineWeight, SubtitleStyle.outlineWeights,
                               SubtitleStyle::weightName) { s, v -> s.copy(outlineWeight = v) }
                    }
                }
                item { Header("Background") }
                item {
                    Choice("Box Opacity", o.backgroundOpacity, server.backgroundOpacity, SubtitleStyle.backgroundOpacities,
                           SubtitleStyle::percent) { s, v -> s.copy(backgroundOpacity = v) }
                }
                if (style.backgroundOpacity > 0) {
                    item {
                        Choice("Box Colour", o.backgroundColor, server.backgroundColor, colors, SubtitleStyle::colorName) { s, v ->
                            s.copy(backgroundColor = v)
                        }
                    }
                }
                item { Header("Placement") }
                item {
                    Choice("Position", o.bottomMargin, server.bottomMargin, SubtitleStyle.margins, SubtitleStyle::marginName) { s, v ->
                        s.copy(bottomMargin = v)
                    }
                }
                item {
                    Text(
                        "Default follows the server's setting for this device or profile; a choice here applies to this device only. Bitmap subtitles (PGS, DVB, DVD) follow the size.",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                if (!o.isEmpty) {
                    item { ListItem(selected = false, onClick = { store.reset() }, headlineContent = { Text("Reset All to Default") }) }
                }
            }
        }
    }
    picking?.let { p -> PickerDialog(p.title, p.options, p.selected, onPick = p.pick, onDismiss = { picking = null }) }
}

/** A 16:9 frame with sample text in the player's subtitle overlay. */
@Composable
private fun SubtitlePreview(style: SubtitleStyle, modifier: Modifier) {
    val sample = remember { listOf(Cue.Builder().setText("The quick brown fox\njumps over the lazy dog.").build()) }
    SubtitleOverlay(
        sample, style, 16f / 9f,
        modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(
            Brush.verticalGradient(listOf(Color(0xFF5C85B8), Color(0xFFDBC79E), Color(0xFF404D38))),
        ),
    )
}

@Composable
private fun Header(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 14.dp, start = 4.dp))
}

private fun onOff(value: Boolean) = if (value) "On" else "Off"
