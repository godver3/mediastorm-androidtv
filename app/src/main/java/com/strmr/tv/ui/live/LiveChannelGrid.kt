package com.strmr.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.strmr.tv.ui.cardFocusBorder
import com.strmr.tv.ui.CardFocusScale
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStore
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.PageMargin

/**
 * Channels as 16:9 cards: logo, what is on now with its progress, a favorite star. More
 * channels load as the grid nears its end; select plays, long press offers favorite / hide.
 */
@Composable
fun LiveChannelGrid(store: LiveStore, now: Long, entry: FocusRequester, modifier: Modifier = Modifier, pick: LivePick? = null) {
    var menuFor by remember { mutableStateOf<LiveChannel?>(null) }
    val channels = store.visibleChannels
    LazyVerticalGrid(
        columns = GridCells.Adaptive(168.dp),
        contentPadding = PaddingValues(horizontal = PageMargin, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.focusRestorer(entry),
    ) {
        itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
            LaunchedEffect(channel.id) { store.needNowNext(channel) }
            if (index >= channels.size - 20) LaunchedEffect(channels.size) { store.loadMore() }
            val current = store.current(channel, now)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Card(
                    onClick = { pick?.onPick?.invoke(channel) ?: PlaybackCoordinator.playLive(channel, current?.title) },
                    onLongClick = { menuFor = channel },
                    scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(),
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(if (index == 0) Modifier.focusRequester(entry) else Modifier),
                ) {
                    Box(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF333333), Color(0xFF1A1A1A)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChannelLogo(channel, 100.dp, 52.dp, 14.sp)
                        current?.let { AiringProgress(it.progress(now), Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth().height(3.dp)) }
                        if (channel.id in store.favorites) {
                            Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = Color(0xFFFFD60A),
                                 modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp))
                        }
                        if (pick?.marked?.let { it.id == channel.id && it.sourceId == channel.sourceId } == true) {
                            Icon(AppIcons.Multiscreen, contentDescription = "On this screen", tint = com.strmr.tv.ui.Accent,
                                 modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(14.dp))
                        }
                        if (channel.id in store.hidden) {
                            Icon(AppIcons.Hidden, contentDescription = "Hidden", tint = Color.White.copy(alpha = 0.5f),
                                 modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(14.dp))
                        }
                    }
                }
                Text(channel.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current?.title ?: channel.group ?: "", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f),
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    menuFor?.let { ChannelMenu(store, it) { menuFor = null } }
}
