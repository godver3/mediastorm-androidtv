package com.strmr.tv.ui.live.multiscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.strmr.tv.live.MultiscreenSession
import com.strmr.tv.live.MultiscreenSlot
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.BrandBackground
import com.strmr.tv.ui.ignoreHeldSelect
import com.strmr.tv.ui.live.ChannelLogo

/** The control bar's height, in dp; the tiles fit above it while it is open. */
const val DOCK_HEIGHT = 132f

/**
 * The bottom dock (Hold OK, or Down below the tiles): the focused screen's details on the left,
 * its actions on the right. Up closes it; Back is the screen's ([MultiscreenModel.back]).
 */
@Composable
fun MultiscreenControlBar(model: MultiscreenModel, state: MultiscreenSession.State, modifier: Modifier = Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val index = state.focusedIndex
    val slot = state.slots.getOrNull(index)
    Row(
        modifier.fillMaxWidth().height(DOCK_HEIGHT.dp).background(BrandBackground)
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp)
            // The held Select that opened the bar mustn't click its first button.
            .ignoreHeldSelect()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) model.reveal()
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                    model.actions = false
                    true
                } else e.key == Key.DirectionDown
            },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockDetails(model, slot, index, state.audioIndex == index && slot?.source != null, Modifier.weight(1f).fillMaxHeight())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ControlButton(Icons.Filled.Add, "Add screen", Modifier.focusRequester(first)) { model.addScreen() }
            ControlButton(AppIcons.SwapSource, if (slot?.channel != null) "Change channel" else "Add channel") { model.changeChannel() }
            if (slot?.channel != null && slot.error != null) ControlButton(Icons.Filled.Refresh, "Retry") { model.retry() }
            if (slot?.channel != null) ControlButton(AppIcons.Remove, "Remove") { model.remove() }
            ControlButton(AppIcons.Multiscreen, "Equal tiles") { model.equalTiles() }
            ControlButton(Icons.AutoMirrored.Filled.ExitToApp, "Guide") { model.exit() }
        }
    }
}

@Composable
private fun DockDetails(model: MultiscreenModel, slot: MultiscreenSlot?, index: Int, audible: Boolean, modifier: Modifier) {
    val channel = slot?.channel
    val schedule = model.epg[channel?.guideKey]
    val current = schedule.airing()
    val next = schedule?.next?.takeIf { it !== current }
    val info = model.streamInfo[index]?.label.orEmpty()
    val status = model.exitError ?: slot?.error ?: if (slot?.loading == true) "Tuning…" else null
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        channel?.let { ChannelLogo(it, 64.dp, 40.dp, 10.sp, namesChannel = false) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Screen ${index + 1}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
                if (audible) Icon(AppIcons.VolumeUp, contentDescription = "Playing audio", tint = Accent, modifier = Modifier.size(13.dp))
            }
            Text(current?.title ?: channel?.name ?: "Add channel", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            channel?.let {
                Text(listOf(it.name, info).filter { s -> s.isNotEmpty() }.joinToString(" · "), fontSize = 11.sp,
                     color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            current?.description?.takeIf { it.isNotEmpty() }?.let {
                Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            status?.let { Text(it, fontSize = 11.sp, color = Color(0xFFFF8A80), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            current?.let { program ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(3.dp).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(program.progress()).background(Accent))
                    }
                    Text(remaining(program), fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                }
            }
            next?.let { Text("Next: ${it.title}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1) }
        }
    }
}

/** An icon over its label, as the live player's guide actions. */
@Composable
private fun ControlButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(74.dp).height(66.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
               verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp,
                 modifier = Modifier.padding(top = 4.dp))
        }
    }
}
