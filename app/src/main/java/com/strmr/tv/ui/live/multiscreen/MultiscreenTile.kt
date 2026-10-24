package com.strmr.tv.ui.live.multiscreen

import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.strmr.tv.live.EpgNowPlaying
import com.strmr.tv.live.EpgProgram
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.MultiscreenSlot
import com.strmr.tv.live.TileSource
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.live.ChannelLogo

/** The hairline sits this far outside a focused tile, in the gap, never over its video. */
private const val HAIRLINE_OUTSET = 3f

/**
 * One screen at its frame: the video (a SurfaceView per tile, placed and never animated, so
 * the surfaces stay disjoint) and its overlay; the focused one gets the TV card hairline in
 * the gap around it. [overlay] is the HUD's visibility; tuning or failed tiles always show
 * their status.
 */
@Composable
fun MultiscreenTile(
    model: MultiscreenModel, index: Int, slot: MultiscreenSlot, frame: TileFrame,
    focused: Boolean, audible: Boolean, overlay: Boolean, alone: Boolean,
) {
    Box(Modifier.offset(frame.x.dp, frame.y.dp).size(frame.width.dp, frame.height.dp).background(Color.Black)) {
        var buffering by remember { mutableStateOf(false) }
        slot.source?.let { source -> key(source) { TileVideo(model, index, source, audible) { buffering = it } } }
        if (slot.loading || (buffering && slot.source != null && slot.error == null)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = Color.White, strokeWidth = 2.dp)
        }
        val status = if (slot.loading) "Tuning…" else slot.error
        if (overlay || status != null) {
            TileOverlay(slot.channel, model.epg[slot.channel?.guideKey], model.streamInfo[index], audible && slot.source != null,
                        status, wide = frame.width >= 300f, Modifier.align(Alignment.BottomStart))
        }
    }
    if (focused && !alone) {
        Box(
            Modifier.offset((frame.x - HAIRLINE_OUTSET).dp, (frame.y - HAIRLINE_OUTSET).dp)
                .size((frame.width + HAIRLINE_OUTSET * 2).dp, (frame.height + HAIRLINE_OUTSET * 2).dp)
                .border(1.dp, Color.White, RoundedCornerShape(4.dp)),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TileVideo(model: MultiscreenModel, index: Int, source: TileSource, audible: Boolean, onBuffering: (Boolean) -> Unit) {
    val context = LocalContext.current
    val tile = remember(source) { TilePlayer(context, source.url) { model.session.fail(index, source) } }
    DisposableEffect(tile) {
        onDispose {
            model.streamInfo.remove(index)
            tile.release()
        }
    }
    LaunchedEffect(tile, audible) { tile.setAudible(audible) }
    val buffering by tile.buffering.collectAsState()
    LaunchedEffect(buffering) { onBuffering(buffering) }
    val info by tile.info.collectAsState()
    LaunchedEffect(info) { model.streamInfo[index] = info }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                player = tile.player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                subtitleView?.visibility = View.GONE
            }
        },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize().focusProperties { canFocus = false },
    )
}

/** The programme on now, from now / next. */
fun EpgNowPlaying?.airing(now: Long = System.currentTimeMillis()): EpgProgram? =
    listOfNotNull(this?.current, this?.next).firstOrNull { it.startMs <= now && it.stopMs > now }

/** "1 hr 5 min remaining". */
fun remaining(program: EpgProgram, now: Long = System.currentTimeMillis()): String {
    val minutes = ((program.stopMs - now + 59_999) / 60_000).coerceAtLeast(0)
    return listOfNotNull(
        (minutes / 60).takeIf { it > 0 }?.let { "$it hr" },
        (minutes % 60).takeIf { it > 0 || minutes < 60 }?.let { "$it min" },
        "remaining",
    ).joinToString(" ")
}

/** A tile's caption: logo, programme (or channel), channel and stream info when wide, the speaker, status. */
@Composable
private fun TileOverlay(
    channel: LiveChannel?, epg: EpgNowPlaying?, info: TileStreamInfo?, audible: Boolean, status: String?,
    wide: Boolean, modifier: Modifier,
) {
    val current = epg.airing()
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black.copy(alpha = 0.95f))),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                channel?.let { ChannelLogo(it, 28.dp, 20.dp, 8.sp, namesChannel = false) }
                Column(Modifier.weight(1f)) {
                    Text(current?.title ?: channel?.name ?: "Add channel", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                         maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (wide && channel != null) {
                        Text(listOf(channel.name, info?.label.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "),
                             fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (audible && channel != null) {
                    Icon(AppIcons.VolumeUp, contentDescription = "Playing audio", tint = Accent, modifier = Modifier.size(16.dp))
                }
            }
            status?.let { Text(it, fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 2) }
        }
    }
}
