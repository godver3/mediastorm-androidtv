package com.strmr.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.strmr.tv.live.EpgProgram
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStore
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.PageMargin

private const val SPAN = 2 * 3_600_000L
private const val STEP = LiveStore.HALF_HOUR
private val CHANNEL_WIDTH = 150.dp
private val ROW_HEIGHT = 40.dp
private val INSET = PageMargin
private val CELL_GAP = 4.dp

/** One focusable slot in a guide row: a programme, or a stretch without guide data. */
private data class GuideCell(val id: Long, val program: EpgProgram?, val start: Long, val end: Long)

/**
 * The window's programmes with gaps filled; gaps under five minutes fold into the next
 * programme so no sliver of a cell is left to focus. `id` is the programme's own start
 * (stable while the window pages), or a gap's start.
 */
private fun layout(programs: List<EpgProgram>, from: Long, to: Long): List<GuideCell> {
    val cells = mutableListOf<GuideCell>()
    var cursor = from
    for (p in programs) {
        if (p.endMs <= cursor || p.startMs >= to) continue
        var start = maxOf(p.startMs, cursor)
        if (start - cursor >= 300_000) cells += GuideCell(cursor, null, cursor, start) else start = cursor
        val end = minOf(p.endMs, to)
        cells += GuideCell(p.startMs, p, start, end)
        cursor = end
    }
    if (to - cursor >= 60_000 || cells.isEmpty()) {
        cells += GuideCell(cursor, null, cursor, to)
    } else {
        val last = cells.removeAt(cells.lastIndex)
        cells += last.copy(end = to)
    }
    return cells
}

/** Where focus should land after the window pages: this channel, at this time. */
private data class PendingFocus(val channel: String, val at: Long)

/**
 * The channel guide: a two-hour window of programmes per channel under a panel for the focused
 * one. The window pages by half an hour when focus runs off either end of a row (as the Apple TV
 * guide does), from half an hour ago to a day ahead; more channels load near the end. Select
 * plays the channel, long press offers favorite / hide.
 */
@Composable
fun LiveGuide(store: LiveStore, now: Long, entry: FocusRequester, modifier: Modifier = Modifier) {
    var windowStart by remember { mutableLongStateOf(LiveStore.halfHour(now)) }
    var focused by remember { mutableStateOf<Pair<LiveChannel, GuideCell>?>(null) }
    var pending by remember { mutableStateOf<PendingFocus?>(null) }
    var menuFor by remember { mutableStateOf<LiveChannel?>(null) }
    val channels = store.visibleChannels
    val earliest = LiveStore.halfHour(now) - STEP
    val latest = LiveStore.halfHour(now) + 24 * 3_600_000L

    Column(modifier) {
        InfoPanel(focused?.first ?: channels.firstOrNull(), focused?.second?.program, now)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cellsWidth = maxWidth - INSET * 2 - CHANNEL_WIDTH
            val dpPerMs = cellsWidth.value / SPAN
            Column {
                TimeRuler(windowStart, dpPerMs)
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = INSET, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.focusRestorer(entry),
                ) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                        LaunchedEffect(channel.id, windowStart) { store.needSchedule(channel, windowStart, windowStart + SPAN) }
                        if (index >= channels.size - 20) LaunchedEffect(channels.size) { store.loadMore() }
                        val cells = layout(store.schedules[channel.guideKey].orEmpty(), windowStart, windowStart + SPAN)
                        Row(Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                            ChannelColumn(channel, channel.id in store.favorites)
                            Box {
                                // Behind the cells: the unfocused ones are translucent, the focused one covers it.
                                NowLine(now, windowStart, dpPerMs)
                                Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                                    cells.forEachIndexed { i, cell ->
                                        val requester = remember { FocusRequester() }
                                        val p = pending
                                        LaunchedEffect(p, cell.id) {
                                            if (p != null && p.channel == channel.id && p.at in cell.start until cell.end) {
                                                pending = null
                                                runCatching { requester.requestFocus() }
                                            }
                                        }
                                        Cell(
                                            cell, now,
                                            width = maxOf(((cell.end - cell.start) * dpPerMs).dp - CELL_GAP, 16.dp),
                                            onClick = { PlaybackCoordinator.playLive(channel, cell.program?.takeIf { it.isAiring() }?.title) },
                                            onLongClick = { menuFor = channel },
                                            onFocus = { focused = channel to cell },
                                            // Off either end of the row: page the window and keep the time.
                                            onEdge = { forward ->
                                                if (forward && cell.end >= windowStart + SPAN && windowStart < latest) {
                                                    pending = PendingFocus(channel.id, windowStart + SPAN)
                                                    windowStart += STEP
                                                    true
                                                } else if (!forward && cell.start <= windowStart && windowStart > earliest) {
                                                    pending = PendingFocus(channel.id, windowStart - 1)
                                                    windowStart -= STEP
                                                    true
                                                } else false
                                            },
                                            modifier = Modifier.focusRequester(requester)
                                                .then(if (index == 0 && i == cells.indexOfFirst { now in it.start until it.end }.coerceAtLeast(0)) Modifier.focusRequester(entry) else Modifier),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    menuFor?.let { ChannelMenu(store, it) { menuFor = null } }
}

@Composable
private fun ChannelColumn(channel: LiveChannel, favorite: Boolean) {
    Row(Modifier.width(CHANNEL_WIDTH).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChannelLogo(channel, 50.dp, 28.dp, 10.sp, namesChannel = false)
        Column(Modifier.weight(1f)) {
            Text(channel.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (favorite) Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = Color(0xFFFFD60A), modifier = Modifier.size(10.dp))
        }
    }
}

/** A flat cell that turns white under focus, the airing programme a shade lighter; no scale, so wide cells don't spill. */
@Composable
private fun Cell(
    cell: GuideCell, now: Long, width: Dp,
    onClick: () -> Unit, onLongClick: () -> Unit, onFocus: () -> Unit, onEdge: (forward: Boolean) -> Boolean,
    modifier: Modifier,
) {
    val airing = cell.program?.isAiring(now) == true
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (airing) 0.16f else 0.07f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        modifier = modifier.width(width).fillMaxHeight()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> onEdge(true)
                    Key.DirectionLeft -> onEdge(false)
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
            Text(
                (if (cell.program != null && cell.program.startMs < cell.start) "‹ " else "") + (cell.program?.title ?: "No Information"),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            cell.program?.let { Text(timeRange(it.startMs, it.endMs), fontSize = 9.sp, maxLines = 1, modifier = Modifier.padding(top = 1.dp)) }
        }
    }
}

/** Half-hour marks over the programme columns. */
@Composable
private fun TimeRuler(windowStart: Long, dpPerMs: Float) {
    Row(Modifier.padding(start = INSET + CHANNEL_WIDTH, bottom = 2.dp)) {
        for (i in 0 until (SPAN / STEP).toInt()) {
            Text(clock(windowStart + i * STEP), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                 color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width((STEP * dpPerMs).dp))
        }
    }
}

@Composable
private fun NowLine(now: Long, windowStart: Long, dpPerMs: Float) {
    if (now < windowStart || now >= windowStart + SPAN) return
    Box(Modifier.offset(x = ((now - windowStart) * dpPerMs).dp).width(2.dp).height(ROW_HEIGHT).background(com.strmr.tv.ui.Accent))
}

/** The focused programme: title, LIVE, times, time left, episode and description, with its channel. */
@Composable
private fun InfoPanel(channel: LiveChannel?, program: EpgProgram?, now: Long) {
    Row(Modifier.fillMaxWidth().height(92.dp).padding(horizontal = INSET), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(program?.title ?: channel?.name ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (program != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (program.isAiring(now)) {
                        Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.Black,
                             modifier = Modifier.background(Color(0xFFE5332A), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    Text(details(program, now), fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
                program.description?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            } else {
                channel?.group?.let { Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)) }
            }
        }
        if (channel != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(160.dp)) {
                ChannelLogo(channel, 110.dp, 50.dp, 18.sp, namesChannel = false)
                Spacer(Modifier.height(4.dp))
                Text(channel.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun details(p: EpgProgram, now: Long): String {
    val parts = mutableListOf(timeRange(p.startMs, p.endMs))
    if (p.isAiring(now)) parts += "${(p.endMs - now) / 60_000} min left"
    else if (p.startMs > now) parts += "in ${(p.startMs - now) / 60_000} min"
    p.episode?.takeIf { it.isNotEmpty() }?.let { parts += it }
    p.categories?.firstOrNull()?.let { parts += it }
    return parts.joinToString(" · ")
}
