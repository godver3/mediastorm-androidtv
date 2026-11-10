package com.strmr.mobile.ui.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.ScrollState
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.strmr.tv.live.GuideCell
import com.strmr.tv.live.GuideLayout
import com.strmr.tv.live.LiveCatchups
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveRecordings
import com.strmr.tv.live.LiveStore
import com.strmr.tv.live.Recording

/**
 * One channel's programmes between [from] and [to], placed by time on a strip [totalWidth]
 * wide that starts at [start]; only the hours around the screen are laid out. A line marks now.
 */
@Composable
fun GuideRowCells(
    store: LiveStore, channel: LiveChannel, from: Long, to: Long, start: Long, dpPerMs: Float, totalWidth: Dp, now: Long,
    scroll: ScrollState, onProgram: (LiveSheetTarget) -> Unit,
) {
    val cells = GuideLayout.cells(store.schedules[channel.guideKey].orEmpty(), from, to)
    val recordings = store.recordings.active.filter { it.channelId == channel.id }
    Box(Modifier.width(totalWidth).fillMaxHeight()) {
        cells.forEach { cell ->
            val x = ((cell.start - start) * dpPerMs).dp
            val w = maxOf(((cell.end - cell.start) * dpPerMs).dp - 2.dp, 4.dp)
            GuideProgramCell(
                cell, now, cellX = x + 1.dp, scroll = scroll,
                recording = cell.program?.let { LiveRecordings.matching(recordings, it, now) },
                replayable = LiveCatchups.replaysOnSelect(channel, cell.program, now),
                onOpen = { onProgram(LiveSheetTarget(channel, cell.program)) },
                modifier = Modifier.offset(x = x + 1.dp).width(w).fillMaxHeight().padding(vertical = 3.dp),
            )
        }
        if (now in start until (start + (totalWidth.value / dpPerMs).toLong())) {
            Box(Modifier.offset(x = ((now - start) * dpPerMs).dp - 1.dp).width(2.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideProgramCell(
    cell: GuideCell, now: Long, cellX: Dp, scroll: ScrollState, recording: Recording?, replayable: Boolean, onOpen: () -> Unit, modifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val p = cell.program
    val airing = p?.isAiring(now) == true
    val ended = p != null && p.stopMs <= now
    val colors = MaterialTheme.colorScheme
    val container = when {
        airing -> colors.primaryContainer
        p == null -> colors.surfaceContainerLow
        else -> colors.surfaceContainerHigh
    }
    val content = when {
        airing -> colors.onPrimaryContainer
        ended -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(container)
            .combinedClickable(onClick = onOpen, onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpen()
            }),
    ) {
        // A programme that began left of the visible guide keeps its title in view: the text
        // slides right with the scroll (read at layout, so scrolling doesn't recompose), until
        // only a short end of the cell is left.
        Column(
            Modifier.fillMaxSize().layout { measurable, constraints ->
                val room = (constraints.maxWidth - 72.dp.toPx()).coerceAtLeast(0f)
                val shift = (scroll.value - cellX.toPx()).coerceIn(0f, room).roundToInt()
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth - shift))
                layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(shift, 0) }
            }.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                recording?.let { RecordingBadge(it, 10.dp) }
                if (replayable) Icon(Icons.Filled.Replay, contentDescription = "Catch-up", tint = content, modifier = Modifier.size(10.dp))
                if (p?.isNew == true) NewTag()
                Text(
                    (if (p != null && p.startMs < cell.start) "‹ " else "") + (p?.title ?: "No Information"),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            p?.let {
                Text(timeRange(it.startMs, it.endMs), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.75f),
                     maxLines = 1)
            }
        }
    }
}
