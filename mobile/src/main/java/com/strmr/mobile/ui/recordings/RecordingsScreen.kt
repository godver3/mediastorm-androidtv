package com.strmr.mobile.ui.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.mobile.ui.shell.Route
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingFilter
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.recordings.RecordingsState
import kotlinx.coroutines.delay

/**
 * Live TV recordings: filter chips with counts (All, Upcoming, Recording, Completed, History),
 * then the list (under All, a section per status). Tap opens a recording's page; the play
 * button plays one that has a file; long press offers every action. Rules & Buffers in the top
 * bar. Refreshes every 10 s, after playback, and on a pull.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(asPage: Boolean) {
    val scope = rememberCoroutineScope()
    val state = remember { RecordingsState(scope) }
    val confirmations = remember { RecordingConfirmations(state) }
    val navigator = LocalNavigator.current
    var menuFor by remember { mutableStateOf<Recording?>(null) }
    val sessionsEnded by PlaybackCoordinator.sessionsEnded.collectAsState()

    LaunchedEffect(sessionsEnded) {
        while (true) {
            state.load(quiet = true)
            delay(10_000)
        }
    }

    PageScaffold(
        "Recordings", showBack = asPage,
        actions = {
            IconButton(onClick = { navigator.push(Route.RecordingRules) }) { Icon(Icons.Filled.Repeat, "Rules & Buffers") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RecordingFilter.entries) { f ->
                    FilterChip(selected = state.filter == f, onClick = { state.filter = f }, label = { Text("${f.label}  ${state.count(f)}") })
                }
            }
            state.error?.let {
                Text("Unable to refresh recordings: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                     modifier = Modifier.padding(horizontal = 16.dp))
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { state.refresh() }, modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.visible.isEmpty() -> Empty(state)
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        RecordingActions.sections(state.recordings, state.filter).forEach { (filter, list) ->
                            if (state.filter == RecordingFilter.ALL) {
                                item(key = "h-${filter.name}") {
                                    Text(filter.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                         modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                                }
                            }
                            items(list, key = { it.id }) { r ->
                                RecordingRow(
                                    r, busy = state.busyId == r.id,
                                    onOpen = { navigator.push(Route.RecordingDetail(r.id)) },
                                    onPlay = { PlaybackCoordinator.playRecording(r) },
                                    onMenu = { menuFor = r },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { r ->
        RecordingMenuSheet(r, RecordingActions.of(r, state.ruleFor(r)), onDismiss = { menuFor = null }) { kind ->
            menuFor = null
            confirmations.run(r, kind)
        }
    }
    RecordingConfirmationDialog(confirmations)
}

@Composable
private fun Empty(state: RecordingsState) {
    val any = state.recordings.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (any) "No ${state.filter.label.lowercase()} recordings" else "Your recordings will appear here",
                     style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(if (any) "Choose another filter to browse your library."
                     else "Long press a channel or a guide programme on Live TV to record it.",
                     color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
