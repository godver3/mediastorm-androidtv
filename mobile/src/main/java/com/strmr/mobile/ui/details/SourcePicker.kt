package com.strmr.mobile.ui.details

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.strmr.mobile.ui.components.cardClicks
import com.strmr.mobile.ui.library.LibraryVersionItem
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.SourceHealth
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.searchSources
import com.strmr.tv.api.sourceHealth
import com.strmr.tv.library.LibraryVersion
import com.strmr.tv.player.ManualSourceTracks
import com.strmr.tv.player.SourceSwitchRules
import com.strmr.tv.ui.details.SourceTracks

/**
 * Choose Source as a full-screen page over the details page: the movie's or episode's library
 * copies first, then the server's indexer and scraper results with their debrid cache / usenet
 * health (pending checks long-polled until they settle). Long press on a result opens its
 * Audio & Subtitles ([SourceTracks]); the tracks chosen there also apply when the result is
 * tapped directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePicker(
    title: DetailsTarget,
    episode: Episode?,
    libraryVersions: List<LibraryVersion>,
    onPick: (SourceResult, ManualSourceTracks?) -> Unit,
    onPickLibrary: (LibraryVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tracks = remember { SourceTracks(scope) }
    var results by remember { mutableStateOf<List<SourceResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val health = remember { mutableStateMapOf<Int, SourceHealth>() }
    LaunchedEffect(Unit) {
        try {
            results = ApiClient.searchSources(SourceSwitchRules.query(title, episode?.code), title, episode?.numbering)
            results.forEach { r -> r.health?.let { health[r.index] = it } }
        } catch (e: Exception) {
            error = e.message ?: "Search failed"
        }
        loading = false
        // Long-polls each pending check until it completes, applying results by index.
        for (token in health.values.filter { it.state == "pending" }.mapNotNull { it.token }.toSet()) {
            var complete = false
            while (!complete) {
                val snapshot = runCatching { ApiClient.sourceHealth(token) }.getOrNull() ?: break
                snapshot.results.forEach { health[it.index] = it.health }
                complete = snapshot.complete
            }
        }
        // Anything still pending (poll failed or token expired) stops spinning.
        health.filterValues { it.state == "pending" }.keys.forEach { health[it] = SourceHealth("unknown") }
    }
    val pick = { result: SourceResult, manual: ManualSourceTracks? ->
        onDismiss()
        onPick(result, manual)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(episode?.let { "${title.name} · ${it.code}" } ?: title.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
                if (libraryVersions.isNotEmpty()) {
                    section(if (libraryVersions.size == 1) "In Your Libraries" else "${libraryVersions.size} in Your Libraries")
                    items(libraryVersions, key = { "library:${it.id}" }) { version ->
                        LibraryVersionItem(version) {
                            onDismiss()
                            onPickLibrary(version)
                        }
                    }
                }
                section(if (!loading && error == null && results.isNotEmpty()) "${results.size} Streams" else "Streams")
                when {
                    loading -> item {
                        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Searching sources…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    error != null -> item { Text(error!!, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error) }
                    results.isEmpty() -> item { Text("No sources found", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else -> items(results, key = { it.index }) { result ->
                        SourceRow(result, health[result.index], tracks.hasChoice(result), onLongClick = { tracks.open(result) }) {
                            pick(result, tracks.manualFor(result))
                        }
                    }
                }
            }
        }
        tracks.openResult?.let { result ->
            TracksSheet(
                release = tracks.release(result),
                state = tracks.probeState(result),
                defaults = tracks.trackDefaults,
                choice = tracks.choice(result),
                onChoice = { tracks.choose(result, it) },
                onRetry = { tracks.retry(result) },
                onPlay = {
                    tracks.close()
                    pick(result, tracks.manualFor(result))
                },
                onDismiss = { tracks.close() },
            )
        }
    }
}

private fun LazyListScope.section(title: String) = item(key = "section-$title") {
    Text(
        title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SourceRow(result: SourceResult, health: SourceHealth?, tracksChosen: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val attrs = result.attributes
    val badges = SourceRowText.badges(attrs)
    val size = result.sizeBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    val details = SourceRowText.details(attrs, size, result.serviceType, result.indexer, tracksChosen)
    ListItem(
        overlineContent = if (badges.isNotEmpty()) { { Text(badges.joinToString("  ·  "), fontWeight = FontWeight.Bold) } } else null,
        headlineContent = { Text(SourceRowText.release(result.title, attrs), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details.joinToString("  ·  "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { HealthBadge(health) },
        modifier = Modifier.cardClicks(onClick, onLongClick),
    )
}

/** Debrid cache / usenet health: green when playable, red when not, a spinner while checking. */
@Composable
private fun HealthBadge(health: SourceHealth?) {
    when (SourceRowText.health(health?.state)) {
        SourceRowText.Health.PENDING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        SourceRowText.Health.GOOD -> Icon(Icons.Filled.CheckCircle, contentDescription = if (health?.state == "cached") "Cached" else "Healthy",
                                          tint = Color(0xFF30D158))
        SourceRowText.Health.BAD -> Icon(Icons.Filled.Cancel, contentDescription = if (health?.state == "not_cached") "Not Cached" else "Unhealthy",
                                         tint = Color(0xFFFF453A))
        SourceRowText.Health.NONE -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.Transparent)
    }
}
