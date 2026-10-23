package com.strmr.tv.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
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
import com.strmr.tv.ui.Accent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The transport's progress bar: played (accent), buffered and remaining, elapsed under the
 * playhead and the time left (with the end time) on the right. While scrubbing the bar shows a
 * second, white playhead at the target with a seek preview above it (the backend's thumbnail
 * nearest the target, or just the time when there is none). Focusing it thickens it.
 */
@Composable
fun ScrubBar(
    controller: PlayerController,
    scrubTarget: Long?,
    modifier: Modifier = Modifier,
    /** Focus requester and keys, applied to the bar itself. */
    barModifier: Modifier = Modifier,
) {
    val position by controller.position.collectAsState()
    val duration by controller.duration.collectAsState()
    val speed by controller.speed.collectAsState()
    val thumbnails = controller.thumbnails
    val thumbList = thumbnails?.thumbnails?.collectAsState()?.value.orEmpty()
    var focused by remember { mutableStateOf(false) }
    val barHeight by animateDpAsState(if (focused) 10.dp else 6.dp, label = "bar")

    fun fraction(ms: Long) = if (duration > 0) (ms.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val buffered = fraction(controller.player.bufferedPosition)
    val shown = scrubTarget ?: position

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth
        Column(Modifier.fillMaxWidth()) {
            // Preview above the scrub target, overflowing upward over the title rather than moving it.
            Box(Modifier.fillMaxWidth().height(0.dp)) {
                if (scrubTarget != null) {
                    val thumb = if (thumbList.isEmpty()) null else thumbnails?.at(scrubTarget)
                    ScrubPreview(
                        thumb?.url, scrubTarget, thumbList.isNotEmpty(),
                        Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true)
                            .offset(x = previewOffset(width, fraction(scrubTarget)), y = (-8).dp),
                    )
                    thumbnails?.let { t -> PrefetchNeighbours(t, scrubTarget) }
                }
            }
            Box(
                barModifier.fillMaxWidth().height(18.dp).onFocusChanged { focused = it.isFocused }.focusable(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(buffered).background(Color.White.copy(alpha = 0.22f)))
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction(position)).background(Accent))
                }
                // Playhead: the knob when focused, the scrub target's white marker while scrubbing.
                if (focused || scrubTarget != null) {
                    val knob = if (scrubTarget != null) 4.dp else 16.dp
                    Box(
                        Modifier.offset(x = (width * fraction(shown)) - knob / 2)
                            .size(width = knob, height = if (scrubTarget != null) 22.dp else 16.dp)
                            .shadow(4.dp, RoundedCornerShape(50))
                            .background(Color.White, RoundedCornerShape(50)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(formatTimestamp(shown / 1000.0), weight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (duration > 0) {
                    val left = ((duration - shown) / speed).toLong().coerceAtLeast(0)
                    Text("-" + formatTimestamp(left / 1000.0), weight = FontWeight.SemiBold)
                    Text("  ·  Ends ${endsAt(left)}", alpha = 0.6f)
                }
            }
        }
    }
}

/** A frame of the target (or a time-only card when there are no thumbnails) and its time. */
@Composable
private fun ScrubPreview(url: String?, targetMs: Long, hasThumbnails: Boolean, modifier: Modifier) {
    Column(modifier.width(PreviewWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        if (hasThumbnails) {
            Box(
                Modifier.width(PreviewWidth).aspectRatio(16f / 9f).shadow(12.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1E))
                    .border(2.dp, Color.White, RoundedCornerShape(10.dp)),
            ) {
                url?.let {
                    AsyncImage(
                        model = thumbnailRequest(it),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(PreviewHeight))
        }
        Text(
            formatTimestamp(targetMs / 1000.0), weight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Warms the image cache with the thumbnails either side of the target, so a held scrub doesn't flicker. */
@Composable
private fun PrefetchNeighbours(thumbnails: PlaybackThumbnails, targetMs: Long) {
    val context = LocalContext.current
    val key = thumbnails.at(targetMs)?.timeMs
    LaunchedEffect(key) {
        for (t in thumbnails.around(targetMs)) context.imageLoader.enqueue(thumbnailRequest(t.url, context))
    }
}

@Composable
private fun thumbnailRequest(url: String) = thumbnailRequest(url, LocalContext.current)

private fun thumbnailRequest(url: String, context: android.content.Context) = ImageRequest.Builder(context)
    .data(url)
    .apply { ApiClient.mediaHeaders.forEach { (k, v) -> addHeader(k, v) } }
    .memoryCacheKey(url)
    .diskCacheKey(url)
    .build()

/** Keeps the preview centred on the target, inside the bar's ends. */
private fun previewOffset(width: Dp, fraction: Float): Dp =
    (width * fraction - PreviewWidth / 2).coerceIn(0.dp, (width - PreviewWidth).coerceAtLeast(0.dp))

private fun endsAt(leftMs: Long): String =
    LocalTime.now().plusSeconds(leftMs / 1000).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun Text(text: String, modifier: Modifier = Modifier, weight: FontWeight? = null, alpha: Float = 1f) =
    androidx.tv.material3.Text(
        text, modifier, color = Color.White.copy(alpha = alpha), fontWeight = weight,
        style = androidx.tv.material3.MaterialTheme.typography.labelLarge,
    )

private val PreviewWidth = 300.dp
private val PreviewHeight = PreviewWidth * 9 / 16
