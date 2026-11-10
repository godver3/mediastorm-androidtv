package com.strmr.mobile.ui.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStore
import com.strmr.tv.player.PlaybackCoordinator

/**
 * Channels as a list (the iPhone's channel list): logo, name, what is on now with its
 * progress and what is next. Tap tunes; long press opens the channel's sheet (watch, record,
 * Choose Source, favorite, hide). More channels load near the end.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveChannelList(store: LiveStore, now: Long, onMenu: (LiveSheetTarget) -> Unit, modifier: Modifier = Modifier) {
    val channels = store.visibleChannels
    val haptics = LocalHapticFeedback.current
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
            LaunchedEffect(channel.id) { store.needNowNext(channel) }
            if (index >= channels.size - 20) LaunchedEffect(channels.size) { store.loadMore() }
            ChannelRow(
                store, channel, now,
                Modifier.combinedClickable(
                    onClick = { PlaybackCoordinator.playLive(channel, store.current(channel, now)?.title) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMenu(LiveSheetTarget(channel, null))
                    },
                ),
            )
        }
        if (store.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(store: LiveStore, channel: LiveChannel, now: Long, modifier: Modifier) {
    val current = store.current(channel, now)
    val next = store.guide.nowNext[channel.guideKey]?.next?.takeIf { it.startMs >= now }
        ?: store.schedules[channel.guideKey]?.firstOrNull { it.startMs >= now }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) { ChannelLogo(channel, 76.dp, 43.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                     modifier = Modifier.weight(1f, fill = false))
                if (channel.id in store.favorites) {
                    Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = FavoriteYellow, modifier = Modifier.size(14.dp))
                }
                if (channel.id in store.hidden) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = "Hidden", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                         modifier = Modifier.size(14.dp))
                }
                store.recordings.on(channel, now = now)?.let { RecordingBadge(it) }
            }
            if (current != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (current.isNew) NewTag()
                    Text(current.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LinearProgressIndicator(progress = { current.progress(now) }, modifier = Modifier.fillMaxWidth().height(3.dp),
                                        drawStopIndicator = {})
                next?.let {
                    Text("Next: ${clock(it.startMs)} ${it.title}", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                channel.group?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
