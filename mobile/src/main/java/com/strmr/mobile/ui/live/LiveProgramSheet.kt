package com.strmr.mobile.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.strmr.tv.live.EpgProgram
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStore
import com.strmr.tv.live.RecordingRules
import com.strmr.tv.player.PlaybackCoordinator

/** What the programme sheet is for: a channel, and the programme tapped (else what is on now). */
data class LiveSheetTarget(val channel: LiveChannel, val program: EpgProgram?)

/**
 * A programme's (or channel's) details and every action on it, as one bottom sheet: the TV
 * guide's info panel and its long-press menu together. Watch (live, from catch-up, Start Over),
 * the recording choices, Choose Source / favorite / hide, then what airs next on the channel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveProgramSheet(store: LiveStore, target: LiveSheetTarget, now: Long, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState()
    val channel = target.channel
    val program = target.program
    val current = store.current(channel, now)
    val shown = program ?: current
    val recordTarget = program?.takeIf { it.stopMs > now } ?: current
    val existing = recordTarget?.let { store.recordings.on(channel, it, now) }
    val rule = recordTarget?.let { store.recordings.seriesRule(it, channel) }
    val run = { action: LiveAction ->
        // Actions that open a player or another sheet close this one first.
        if (action is LiveAction.WatchLive || action is LiveAction.Catchup || action is LiveAction.ChooseSource) onDismiss()
        store.perform(action, channel)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item { Header(store, channel, shown, now) }
            section("Watch", LiveActions.watch(channel, program, current, now), run)
            if (program == null || program.stopMs > now) {
                section("Record", LiveActions.record(program, recordTarget, existing, rule, now), run)
            }
            section("Channel", LiveActions.channel(channel, channel.id in store.favorites, channel.id in store.hidden), run)
            val later = upcoming(store, channel, program, now)
            if (later.isNotEmpty()) {
                item { SectionTitle("Later on ${channel.name}") }
                items(later, key = { it.startMs }) { p ->
                    ListItem(
                        headlineContent = { Text(p.title, maxLines = 1) },
                        supportingContent = p.episode?.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1) } },
                        trailingContent = { Text(clock(p.startMs), style = MaterialTheme.typography.labelMedium) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, actions: List<LiveAction>, run: (LiveAction) -> Unit) {
    if (actions.isEmpty()) return
    item { HorizontalDivider(Modifier.padding(vertical = 4.dp)); SectionTitle(title) }
    items(actions, key = { "$title|${it.label}" }) { a ->
        ListItem(
            headlineContent = { Text(a.label) },
            leadingContent = { Icon(icon(a), contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { run(a) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
         modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

private fun icon(a: LiveAction): ImageVector = when (a) {
    is LiveAction.WatchLive -> Icons.Filled.PlayArrow
    is LiveAction.Catchup -> Icons.Filled.Replay
    is LiveAction.CancelRecording -> Icons.Filled.StopCircle
    is LiveAction.Record -> Icons.Filled.FiberManualRecord
    is LiveAction.RecordSeries, is LiveAction.SwitchSeries -> Icons.Filled.Repeat
    is LiveAction.CancelSeries -> Icons.Filled.Block
    is LiveAction.RecordBlock -> Icons.Filled.Timer
    is LiveAction.ChooseSource -> Icons.Filled.Layers
    is LiveAction.Favorite -> Icons.Filled.Star
    is LiveAction.Hide -> Icons.Filled.Visibility
}

@Composable
private fun Header(store: LiveStore, channel: LiveChannel, program: EpgProgram?, now: Long) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(channel, 72.dp, 40.dp, namesChannel = false)
            Column {
                Text(channel.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                channel.group?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (program == null) {
            Text("No guide information", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Text(program.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (program.isAiring(now)) Pill("LIVE", RecordingRed, Color.White)
            if (program.isNew) NewTag(prominent = true)
            store.recordings.on(channel, program, now)?.let {
                RecordingBadge(it)
                Text(RecordingRules.statusLabel(it.status), style = MaterialTheme.typography.labelMedium, color = RecordingRed)
            }
        }
        Text(programDetails(program, now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (program.isAiring(now)) {
            LinearProgressIndicator(progress = { program.progress(now) }, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
        program.description?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Up to six programmes after the shown one (or after now) from the loaded guide, else now / next's next. */
private fun upcoming(store: LiveStore, channel: LiveChannel, program: EpgProgram?, now: Long): List<EpgProgram> {
    val from = maxOf(program?.stopMs ?: now, now)
    val later = store.schedules[channel.guideKey].orEmpty().filter { it.startMs >= from }.take(6)
    if (later.isNotEmpty() || program != null) return later
    return listOfNotNull(store.guide.nowNext[channel.guideKey]?.next)
}

/** Runs one sheet action against the store and the player. */
fun LiveStore.perform(action: LiveAction, channel: LiveChannel) {
    val rec = recordings
    when (action) {
        is LiveAction.WatchLive -> PlaybackCoordinator.playLive(channel, action.program?.takeIf { it.isAiring() }?.title)
        is LiveAction.Catchup -> PlaybackCoordinator.playCatchup(channel, action.program)
        is LiveAction.CancelRecording -> rec.cancel(action.recording)
        is LiveAction.Record -> rec.record(action.program, channel)
        is LiveAction.RecordSeries -> rec.recordSeries(action.program, channel, action.newOnly)
        is LiveAction.SwitchSeries -> rec.setSeries(action.rule, action.newOnly)
        is LiveAction.CancelSeries -> rec.cancelSeries(action.rule)
        is LiveAction.RecordBlock -> rec.recordTwoHours(channel)
        is LiveAction.ChooseSource -> chooseSource(channel)
        is LiveAction.Favorite -> toggleFavorite(channel)
        is LiveAction.Hide -> toggleHidden(channel)
    }
}
