package com.strmr.tv.ui.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.api.ApiClient
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsStreamMatch
import com.strmr.tv.sports.sportsStreams
import com.strmr.tv.ui.pageBackground
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.liveStremioStreams
import com.strmr.tv.sports.SportsAutoPlay
import com.strmr.tv.sports.SportsStreamLookup
import com.strmr.tv.sports.SportsStreamOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/** A game's stream picker. */
@Composable
fun SportsStreamPicker(game: SportsGame, onPick: (SportsStreamMatch) -> Unit, onDismiss: () -> Unit) =
    SportsStreamPicker(SportsWatchTarget.game(game), onPick, onDismiss)

/**
 * The live channels that look like they carry an event (`/api/sports/game/{id}/streams`),
 * strongest match first. Picking one tunes it in the Live TV player; a single strong match of a
 * live event plays at once. A Stremio addon channel lists each of its streams
 * ([SportsStreamOptions]). "Search All Sources" widens the search past the admin's default
 * sources.
 */
@Composable
fun SportsStreamPicker(target: SportsWatchTarget, onPick: (SportsStreamMatch) -> Unit, onDismiss: () -> Unit) {
    var matches by remember { mutableStateOf<List<SportsStreamMatch>>(emptyList()) }
    var lookups by remember { mutableStateOf<Map<String, SportsStreamLookup>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var broad by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val pick by rememberUpdatedState(onPick)
    val rows = remember(matches, lookups) { SportsStreamOptions.rows(matches, lookups) }

    // Leaving the picker (or searching again) cancels the lookups still running.
    LaunchedEffect(broad) {
        loading = true
        val groups = try {
            ApiClient.sportsStreams(target.id, broad, target.kind, target.parentId)
                .filter { (it.confidenceTier == "strong" || it.confidenceTier == "possible") && it.primary.channelUrl.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't search channels"
            loading = false
            return@LaunchedEffect
        }
        val feeds = groups.flatMap { listOf(it.primary) + it.alternatives.orEmpty() }
            .filter { it.channelUrl.isNotEmpty() }.distinctBy { it.key }
        // Strong matches first, otherwise in the server's order.
        val found = feeds.filter { it.isStrong } + feeds.filterNot { it.isStrong }
        matches = found
        error = if (found.isEmpty()) "No channel looks like it carries this ${if (target.kind == null) "game" else "session"}." else null
        // One clear answer for a game in progress: no need to ask (once its streams are known).
        val candidate = SportsStreamOptions.autoPlayCandidate(groups, broad, target.live)
        val urls = SportsStreamOptions.lookupUrls(found).filter { lookups[it] !is SportsStreamLookup.Found }
            .sortedBy { it != candidate?.channelUrl }
        lookups = lookups + urls.associateWith { SportsStreamLookup.Pending }
        when (val decision = SportsStreamOptions.autoPlay(candidate, lookups)) {
            is SportsAutoPlay.Play -> return@LaunchedEffect pick(decision.match)
            SportsAutoPlay.Wait -> Unit
            SportsAutoPlay.Show -> loading = false
        }
        val permits = Semaphore(SportsStreamOptions.CONCURRENT_LOOKUPS)
        coroutineScope {
            urls.forEach { url ->
                launch {
                    val channel = found.first { it.channelUrl == url }.channel
                    val result = permits.withPermit { lookUp(channel) }
                    lookups = lookups + (url to result)
                    if (loading && url == candidate?.channelUrl) {
                        (SportsStreamOptions.autoPlay(candidate, lookups) as? SportsAutoPlay.Play)?.let { pick(it.match) }
                        loading = false
                    }
                }
            }
        }
    }
    LaunchedEffect(loading) { if (!loading) runCatching { first.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().pageBackground()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 80.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text(target.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (loading) "Looking for live channels" else if (rows.size == 1) "1 stream" else "${rows.size} streams",
                        color = SportsMuted, modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                if (loading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            Text(if (broad) "Searching all sources…" else "Searching channels…", color = SportsMuted)
                        }
                    }
                } else {
                    error?.let { item { Text(it, color = SportsMuted) } }
                    items(rows, key = { it.key }) { match ->
                        ListItem(
                            selected = false,
                            onClick = { onPick(match) },
                            leadingContent = {
                                AsyncImage(model = match.channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit,
                                           modifier = Modifier.size(width = 64.dp, height = 36.dp))
                            },
                            headlineContent = { Text(match.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Column {
                                    Text(details(match), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (match.loadingStreams) Text("Loading streams…", fontSize = 12.sp, color = SportsMuted)
                                }
                            },
                            trailingContent = {
                                Text(if (match.isStrong) "Strong match" else "Possible match", fontSize = 12.sp,
                                     color = if (match.isStrong) Color(0xFF34C759) else SportsMuted)
                            },
                            modifier = if (match.key == rows.first().key) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                    if (!broad) {
                        item {
                            ListItem(
                                selected = false, onClick = { broad = true },
                                headlineContent = { Text("Search All Sources") },
                                modifier = (if (rows.isEmpty()) Modifier.focusRequester(first) else Modifier).padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The channel's streams, or [SportsStreamLookup.Failed] (the plain row stays and the server picks). */
private suspend fun lookUp(channel: LiveChannel): SportsStreamLookup = try {
    withTimeout(SportsStreamOptions.LOOKUP_TIMEOUT_MS) {
        SportsStreamLookup.Found(SportsStreamOptions.clean(ApiClient.liveStremioStreams(channel)))
    }
} catch (e: TimeoutCancellationException) {
    SportsStreamLookup.Failed
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    SportsStreamLookup.Failed
}

private fun details(match: SportsStreamMatch): String {
    val percent = match.confidence?.let { "${Math.round(it * 100)}%" }
    return listOfNotNull(match.sourceName, match.programTitle, match.matchReason, percent).filter { it.isNotEmpty() }.joinToString(" · ")
}
