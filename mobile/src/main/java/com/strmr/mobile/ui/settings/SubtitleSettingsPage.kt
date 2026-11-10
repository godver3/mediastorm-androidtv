package com.strmr.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.player.SubtitleStyle
import com.strmr.tv.player.SubtitleStyle.Overrides
import com.strmr.tv.player.SubtitleStyleStore

/**
 * Settings › Subtitle Style: the device's [SubtitleStyle], each setting over the server's
 * ("Default" follows it), with a rough preview on a 16:9 frame.
 */
@Composable
fun SubtitleSettingsPage() {
    val store = SubtitleStyleStore
    val style = store.style
    val server = store.server
    val o = store.overrides
    LaunchedEffect(Unit) { runCatching { store.adopt(ApiClient.playbackSettings().subtitleStyle) } }
    val colors = SubtitleStyle.palette.map { it.second }

    /** A row whose picker offers Default (the server's value) and [choices]. */
    @Composable
    fun <T> Choice(title: String, override: T?, serverValue: T, choices: List<T>, name: (T) -> String, write: (Overrides, T?) -> Overrides) {
        val all = (choices + listOfNotNull(override, serverValue)).distinct()
        OverrideRow(title, all.map(name), override?.let { all.indexOf(it) }, name(serverValue)) { i ->
            store.update { write(it, i?.let(all::get)) }
        }
    }

    SettingsPage("Subtitle Style") {
        Preview(style)
        SettingsSection("Text") {
            Choice("Size", o.size, server.size, SubtitleStyle.sizes, SubtitleStyle::sizeName) { s, v -> s.copy(size = v) }
            Choice("Colour", o.textColor, server.textColor, colors, SubtitleStyle::colorName) { s, v -> s.copy(textColor = v) }
            Choice("Opacity", o.textOpacity, server.textOpacity, SubtitleStyle.opacities, SubtitleStyle::percent) { s, v -> s.copy(textOpacity = v) }
            Choice("Bold", o.bold, server.bold, listOf(true, false), ::onOff) { s, v -> s.copy(bold = v) }
        }
        SettingsSection("Edge") {
            Choice("Style", o.edge, server.edge, SubtitleStyle.Edge.entries, { it.label }) { s, v -> s.copy(edge = v) }
            if (style.edge == SubtitleStyle.Edge.OUTLINE) {
                Choice("Outline Colour", o.outlineColor, server.outlineColor, colors, SubtitleStyle::colorName) { s, v -> s.copy(outlineColor = v) }
                Choice("Outline Width", o.outlineWeight, server.outlineWeight, SubtitleStyle.outlineWeights, SubtitleStyle::weightName) { s, v ->
                    s.copy(outlineWeight = v)
                }
            }
        }
        SettingsSection("Background") {
            Choice("Box Opacity", o.backgroundOpacity, server.backgroundOpacity, SubtitleStyle.backgroundOpacities, SubtitleStyle::percent) { s, v ->
                s.copy(backgroundOpacity = v)
            }
            if (style.backgroundOpacity > 0) {
                Choice("Box Colour", o.backgroundColor, server.backgroundColor, colors, SubtitleStyle::colorName) { s, v -> s.copy(backgroundColor = v) }
            }
        }
        SettingsSection(
            "Placement",
            "Default follows the server's setting for this device or profile; a choice here applies to this device only. Bitmap subtitles (PGS, DVB, DVD) follow the size.",
        ) {
            Choice("Position", o.bottomMargin, server.bottomMargin, SubtitleStyle.margins, SubtitleStyle::marginName) { s, v -> s.copy(bottomMargin = v) }
        }
        if (!o.isEmpty) SettingsSection { SettingsRow("Reset All to Default") { store.reset() } }
    }
}

/** Sample text on a 16:9 frame in roughly the player's look: colour, weight, edge, box and position. */
@Composable
private fun Preview(style: SubtitleStyle) {
    // The frame is a phone-width stand-in for a 1080-point-tall video.
    Box(
        Modifier.padding(top = 16.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF5C85B8), Color(0xFFDBC79E), Color(0xFF404D38)))),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val scale = 0.18f
        val text = Color(SubtitleStyle.argb(style.textColor, style.textOpacity))
        val shadow = when (style.edge) {
            SubtitleStyle.Edge.SHADOW -> Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 4f)
            SubtitleStyle.Edge.OUTLINE -> Shadow(Color(SubtitleStyle.argb(style.outlineColor, 1.0)), blurRadius = (2 + style.outlineWeight * 6).toFloat())
            SubtitleStyle.Edge.NONE -> null
        }
        Text(
            "The quick brown fox\njumps over the lazy dog.",
            style = TextStyle(
                color = text, fontSize = (SubtitleStyle.BASE_POINTS * style.size * scale).sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal, shadow = shadow, textAlign = TextAlign.Center,
            ),
            modifier = Modifier.padding(bottom = (style.bottomMargin * scale).dp)
                .background(Color(SubtitleStyle.argb(style.backgroundColor, style.backgroundOpacity)), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
