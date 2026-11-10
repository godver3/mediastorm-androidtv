package com.strmr.mobile.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.formatTimestamp
import com.strmr.tv.api.mediaHeaders
import com.strmr.tv.player.PlaybackThumbnails
import com.strmr.tv.player.PlayerController
import com.strmr.tv.player.ProgrammeTimeline
import com.strmr.tv.player.Timeshift
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The phone transport's seek bar: a Material slider over played, buffered (a catch-up
 * programme's aired part) and remaining, elapsed and time left (with the end time) below.
 * Dragging moves a target, not the playback, with the seek preview above the thumb (the
 * backend's thumbnail nearest the target, or just the time); lifting the finger seeks once.
 * A timeshift channel's bar is its window, labelled with the playhead's time of day and how far
 * behind live it is, as on the TV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(
    controller: PlayerController,
    timeline: ProgrammeTimeline?,
    live: Boolean,
    onScrubbing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerPosition by controller.position.collectAsState()
    val playerDuration by controller.duration.collectAsState()
    val position = timeline?.positionMs ?: playerPosition
    val duration = timeline?.lengthMs ?: playerDuration
    val speed by controller.speed.collectAsState()
    val playerBehindLive by controller.behindLive.collectAsState()
    val behindLive = timeline?.behindLiveMs ?: playerBehindLive
    val thumbnails = controller.thumbnails
    val thumbList = thumbnails?.thumbnails?.collectAsState()?.value.orEmpty()
    var target by remember { mutableStateOf<Long?>(null) }
    val shown = target ?: position
    val buffered = timeline?.let { barFraction(it.availableMs, duration) } ?: barFraction(controller.player.bufferedPosition, duration)
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth
        Column(Modifier.fillMaxWidth()) {
            // The preview above the target, overflowing upward over the controls rather than moving them.
            Box(Modifier.fillMaxWidth().height(0.dp)) {
                target?.let { t ->
                    val label = if (live) {
                        val behind = (behindLive + position - t).coerceAtLeast(0)
                        if (Timeshift.isAtLive(behind)) "LIVE" else "-" + formatTimestamp(behind / 1000.0)
                    } else formatTimestamp(t / 1000.0)
                    val thumb = if (thumbList.isEmpty()) null else thumbnails?.at(t)
                    ScrubPreview(
                        thumb?.url, label, thumbList.isNotEmpty(),
                        Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true)
                            .offset(x = previewOffset(width, barFraction(t, duration)), y = (-4).dp),
                    )
                    thumbnails?.let { PrefetchNeighbours(it, t) }
                }
            }
            Slider(
                value = barFraction(shown, duration),
                onValueChange = { f ->
                    if (target == null) onScrubbing(true)
                    target = barPosition(f, duration).coerceAtMost(controller.scrubEnd().coerceAtLeast(0))
                },
                onValueChangeFinished = {
                    target?.let { controller.seekTo(it) }
                    target = null
                    onScrubbing(false)
                },
                enabled = controller.seekable && duration > 0,
                thumb = {
                    Box(Modifier.size(if (target != null) 20.dp else 14.dp).background(accent, CircleShape))
                },
                track = { state ->
                    BarTrack(state.value, buffered, accent)
                },
                modifier = Modifier.fillMaxWidth().height(32.dp),
            )
            if (live) {
                val behind = if (target != null) (behindLive + position - shown).coerceAtLeast(0) else behindLive
                LiveTimes(behind, timeline)
            } else Row(Modifier.fillMaxWidth()) {
                Label(formatTimestamp(shown / 1000.0))
                Spacer(Modifier.weight(1f))
                if (duration > 0) {
                    val left = ((duration - shown) / speed).toLong().coerceAtLeast(0)
                    Label("-" + formatTimestamp(left / 1000.0) + "  ·  Ends " + clock(LocalTime.now().plusSeconds(left / 1000)))
                }
            }
        }
    }
}

/** Played (accent), buffered (lighter) and the rest, as a rounded bar. */
@Composable
private fun BarTrack(played: Float, buffered: Float, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.35f), size = Size(size.width * buffered.coerceIn(0f, 1f), size.height), cornerRadius = r)
        drawRoundRect(accent, topLeft = Offset.Zero, size = Size(size.width * played.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

/** A timeshift bar's labels: the playhead's time of day, and how far behind live (or LIVE). */
@Composable
private fun LiveTimes(behindMs: Long, timeline: ProgrammeTimeline?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(clock(LocalTime.now().minusSeconds(behindMs / 1000)))
        Spacer(Modifier.weight(1f))
        timeline?.program?.let {
            Text("${it.title}  ·  ${clock(it.startMs)} – ${clock(it.stopMs)}", style = MaterialTheme.typography.labelMedium,
                 color = Color.White.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.weight(2f, fill = false).padding(horizontal = 12.dp))
            Spacer(Modifier.weight(1f))
        }
        if (Timeshift.isAtLive(behindMs)) LiveBadge() else Label("-" + formatTimestamp(behindMs / 1000.0) + " behind live")
    }
}

/** The red LIVE capsule (a live channel without a timeshift window shows it in place of the bar). */
@Composable
fun LiveBadge(text: String = "LIVE", modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium,
        modifier = modifier.background(LiveRed, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
}

/** A frame of the target (or a time-only card when there are no thumbnails) and its time. */
@Composable
private fun ScrubPreview(url: String?, label: String, hasThumbnails: Boolean, modifier: Modifier) {
    Column(modifier.width(PreviewWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        if (hasThumbnails) {
            Box(
                Modifier.width(PreviewWidth).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1C1C1E)).border(1.5.dp, Color.White, RoundedCornerShape(8.dp)),
            ) {
                url?.let {
                    AsyncImage(model = thumbnailRequest(it, LocalContext.current), contentDescription = null,
                               contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
            }
        }
        Text(
            label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Warms the image cache with the thumbnails either side of the target, so a drag doesn't flicker. */
@Composable
private fun PrefetchNeighbours(thumbnails: PlaybackThumbnails, targetMs: Long) {
    val context = LocalContext.current
    val key = thumbnails.at(targetMs)?.timeMs
    LaunchedEffect(key) {
        for (t in thumbnails.around(targetMs)) context.imageLoader.enqueue(thumbnailRequest(t.url, context))
    }
}

/** Thumbnails come from the server, with the app's credentials. */
private fun thumbnailRequest(url: String, context: android.content.Context) = ImageRequest.Builder(context)
    .data(url)
    .apply { ApiClient.mediaHeaders.forEach { (k, v) -> addHeader(k, v) } }
    .memoryCacheKey(url)
    .diskCacheKey(url)
    .build()

/** Keeps the preview centred on the target, inside the bar's ends. */
private fun previewOffset(width: Dp, fraction: Float): Dp =
    (width * fraction - PreviewWidth / 2).coerceIn(0.dp, (width - PreviewWidth).coerceAtLeast(0.dp))

private fun clock(time: LocalTime): String = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun clock(epochMs: Long): String =
    clock(java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime())

private val PreviewWidth = 176.dp
val LiveRed = Color(0xFFE5332A)
