package com.strmr.mobile.ui.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strmr.tv.live.LiveCatchups
import com.strmr.tv.live.LiveGuideData
import com.strmr.tv.live.LiveStore
import com.strmr.tv.player.PlaybackCoordinator
import kotlinx.coroutines.launch

private const val HOUR = 3_600_000L
private val ROW_HEIGHT = 62.dp

/**
 * The phone guide (the iPhone's `MobileGuideView`): a time grid that scrolls sideways under a
 * time ruler, the channel column pinned on the left and a line at the current time. About an
 * hour and a half fits across a phone (three hours on a tablet); it runs from what the
 * provider's catch-up still holds (else half an hour ago) to a day ahead. Every row shares one
 * scroll state, so dragging any row moves the whole grid. Tapping a channel tunes it; tapping a
 * programme opens its sheet ([onProgram]); long press does too, for the channel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveGuideGrid(store: LiveStore, now: Long, onProgram: (LiveSheetTarget) -> Unit, modifier: Modifier = Modifier) {
    val channels = store.visibleChannels
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 600.dp
        val channelWidth = if (compact) 92.dp else 190.dp
        val visibleMs = if (compact) 90 * 60_000L else 3 * HOUR
        val dpPerMs = maxOf((maxWidth - channelWidth).value, 200f) / visibleMs
        val start = remember {
            LiveGuideData.halfHour(now) - LiveCatchups.guideHistory(channels).coerceIn(LiveGuideData.HALF_HOUR, 24 * HOUR)
        }
        val end = LiveGuideData.halfHour(now) + 24 * HOUR
        val totalWidth = ((end - start) * dpPerMs).dp
        val scroll = rememberScrollState()
        val pxPerMs = with(LocalDensity.current) { dpPerMs.dp.toPx() }
        val scope = rememberCoroutineScope()
        fun scrollTo(time: Long) = scope.launch { scroll.animateScrollTo(((time - start) * pxPerMs).toInt().coerceAtLeast(0)) }
        // Quantised, so rows reload their guide data by the half hour, not every scrolled pixel.
        val leading by remember(pxPerMs) { derivedStateOf { LiveGuideData.halfHour(start + (scroll.value / pxPerMs).toLong()) } }
        LaunchedEffect(pxPerMs) { scroll.scrollTo(((LiveGuideData.halfHour(now) - 15 * 60_000L - start) * pxPerMs).toInt().coerceAtLeast(0)) }
        val from = maxOf(start, leading - 90 * 60_000L)
        val to = minOf(end, from + visibleMs + 4 * HOUR)

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(shortDay(leading), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { scrollTo(leading - HOUR) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier") }
                TextButton(onClick = { scrollTo(LiveGuideData.halfHour(System.currentTimeMillis()) - 15 * 60_000L) }) { Text("Now") }
                IconButton(onClick = { scrollTo(leading + HOUR) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later") }
            }
            Ruler(start, end, dpPerMs, channelWidth, totalWidth, scroll)
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                    LaunchedEffect(channel.id, leading) { store.needSchedule(channel, leading, leading + visibleMs) }
                    if (index >= channels.size - 20) LaunchedEffect(channels.size) { store.loadMore() }
                    Row(Modifier.height(ROW_HEIGHT)) {
                        GuideChannelCell(store, channel, channelWidth, now,
                                         onTune = { PlaybackCoordinator.playLive(channel, store.current(channel, now)?.title) },
                                         onMenu = { onProgram(LiveSheetTarget(channel, null)) })
                        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                            GuideRowCells(store, channel, from, to, start, dpPerMs, totalWidth, now, scroll, onProgram)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Half-hour marks over the programme columns, scrolling with the grid. */
@Composable
private fun Ruler(start: Long, end: Long, dpPerMs: Float, channelWidth: Dp, totalWidth: Dp, scroll: ScrollState) {
    Row(Modifier.fillMaxWidth().height(28.dp)) {
        Box(Modifier.width(channelWidth))
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Row(Modifier.width(totalWidth)) {
                var t = start
                while (t < end) {
                    Text(rulerLabel(t), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                         color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                         modifier = Modifier.width((LiveGuideData.HALF_HOUR * dpPerMs).dp).padding(start = 6.dp, top = 6.dp))
                    t += LiveGuideData.HALF_HOUR
                }
            }
        }
    }
}

/** "9:30 PM"; midnight names the day it starts. */
private fun rulerLabel(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return if (t.hour == 0 && t.minute == 0) "${shortDay(ms)} ${clock(ms)}" else clock(ms)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideChannelCell(
    store: LiveStore, channel: com.strmr.tv.live.LiveChannel, width: Dp, now: Long, onTune: () -> Unit, onMenu: () -> Unit,
) {
    Box(
        Modifier.width(width).fillMaxHeight().background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onTune, onLongClick = onMenu),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelLogo(channel, 64.dp, 36.dp)
            if (width > 120.dp) {
                Text(channel.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (channel.id in store.favorites) {
            Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = FavoriteYellow,
                 modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(10.dp))
        }
        store.recordings.on(channel, now = now)?.let {
            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)) { RecordingBadge(it, 10.dp) }
        }
    }
}
