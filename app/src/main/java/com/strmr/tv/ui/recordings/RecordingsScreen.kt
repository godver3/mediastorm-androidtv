package com.strmr.tv.ui.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingFilter
import com.strmr.tv.live.RecordingRules
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.pageBackground
import kotlinx.coroutines.delay

private val Muted = Color.White.copy(alpha = 0.6f)
val RecordingRed = Color(0xFFFF453A)

/**
 * Live TV recordings (Expo's TV `TVRecordings`): filters (All, Upcoming, Recording, Completed,
 * History) with counts, the list on the left, the selected recording's details and actions on
 * the right (Right from the list, or Select). Long press on a row opens the same actions.
 * Refreshes every 10 s, and after playback. [asPage]: opened from Live's More menu, over the
 * tabs, rather than as its own menu entry.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RecordingsScreen(asPage: Boolean = false) {
    val scope = rememberCoroutineScope()
    val state = remember { RecordingsState(scope) }
    val actionsFocus = remember { FocusRequester() }
    val firstFilter = remember { FocusRequester() }
    val selectedRow = remember { FocusRequester() }
    var menuFor by remember { mutableStateOf<Recording?>(null) }
    var confirm by remember { mutableStateOf<Pair<Recording, Boolean>?>(null) }
    val sessionsEnded by PlaybackCoordinator.sessionsEnded.collectAsState()

    LaunchedEffect(sessionsEnded) {
        while (true) {
            state.load(quiet = true)
            delay(10_000)
        }
    }
    if (asPage) LaunchedEffect(Unit) { runCatching { firstFilter.requestFocus() } }
    // The deleted row (or its action) had focus: hand it to the row now selected, else the filters.
    LaunchedEffect(state.deletions) {
        if (state.deletions == 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { selectedRow.requestFocus() }.onFailure { runCatching { firstFilter.requestFocus() } }
    }

    Column(
        Modifier.fillMaxSize().then(if (asPage) Modifier.pageBackground() else Modifier)
            .padding(top = if (asPage) 36.dp else 76.dp, start = PageMargin, end = PageMargin, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recordings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${state.recordings.size} saved & scheduled", color = Muted)
        }
        Row(
            Modifier.padding(vertical = 14.dp).focusRestorer(firstFilter),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordingFilter.entries.forEachIndexed { i, f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { state.filter = f; state.selectedId = null },
                    modifier = if (i == 0) Modifier.focusRequester(firstFilter) else Modifier,
                ) { Text("${f.label}  ${state.count(f)}") }
            }
            Spacer(Modifier.weight(1f))
            if (state.refreshing) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            }
            IconButton(onClick = { state.refresh() }, enabled = !state.refreshing) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        state.error?.let { Text("Unable to refresh recordings: $it", color = RecordingRed, modifier = Modifier.padding(bottom = 8.dp)) }

        val selected = state.selected
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
            }
            selected == null -> Empty(state)
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                LazyColumn(
                    Modifier.weight(0.42f).fillMaxHeight().bleed(RowBleed).fadeTop(ListFade).focusRestorer(),
                    contentPadding = PaddingValues(start = RowBleed, end = RowBleed, top = ListFade, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.visible, key = { it.id }) { r ->
                        RecordingRow(
                            r, selected = r.id == selected.id,
                            onFocus = { state.selectedId = r.id },
                            onClick = { runCatching { actionsFocus.requestFocus() } },
                            onLongClick = { menuFor = r },
                            modifier = if (r.id == selected.id) Modifier.focusRequester(selectedRow) else Modifier,
                        )
                    }
                }
                RecordingDetail(
                    selected, busy = state.busyId == selected.id, actionsFocus = actionsFocus,
                    onPlay = { PlaybackCoordinator.playRecording(it) },
                    onCancel = { confirm = it to false },
                    onDelete = { confirm = it to true },
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            }
        }
    }

    menuFor?.let { r ->
        RecordingMenu(r, onPlay = { PlaybackCoordinator.playRecording(r) }, onCancel = { confirm = r to false },
                      onDelete = { confirm = r to true }, onDismiss = { menuFor = null })
    }
    confirm?.let { (r, delete) ->
        RecordingConfirmation(r, delete, onConfirm = { if (delete) state.delete(r) else state.cancel(r) }, onDismiss = { confirm = null })
    }
}

private val ListFade = 20.dp
/** Room either side for a focused row's grow, which the fade's layer would otherwise clip. */
private val RowBleed = 12.dp

/** Lays out [amount] wider on each side than given, without moving its neighbours. */
private fun Modifier.bleed(amount: Dp) = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Rows scrolled up past the list's top fade out there, so none sits half cut under the filters. */
private fun Modifier.fadeTop(height: Dp) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = height.toPx()),
                 blendMode = BlendMode.DstIn)
    }

@Composable
private fun RecordingRow(
    r: Recording, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(RecordingRules.title(r), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (RecordingRules.isRecording(r)) Text("●", color = RecordingRed)
            }
        },
        supportingContent = {
            Column {
                Text(r.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${RecordingRules.statusLabel(r.status)} · ${RecordingRules.dateTime(r.startAt)}", maxLines = 1,
                     style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() },
    )
}

@Composable
private fun Empty(state: RecordingsState) {
    val any = state.recordings.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (any) "No ${state.filter.label.lowercase()} recordings" else "Your recordings will appear here",
             style = MaterialTheme.typography.titleLarge)
        Text(if (any) "Choose another filter to browse your library." else "Record a program from the Live TV guide to get started.",
             color = Muted)
    }
}
