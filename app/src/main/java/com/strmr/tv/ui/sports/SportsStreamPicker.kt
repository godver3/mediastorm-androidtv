package com.strmr.tv.ui.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.CancellationException

/**
 * What the stream picker looks for (Expo `SportsWatchTarget`): a game, or a race session with
 * the [kind] (`racing`) and weekend ([parentId]) the server resolves it by.
 */
data class SportsWatchTarget(
    val id: String,
    val title: String,
    val live: Boolean,
    val kind: String? = null,
    val parentId: String? = null,
) {
    companion object {
        fun game(game: SportsGame) = SportsWatchTarget(game.id, game.displayTitle, game.lifecycle == SportsLifecycle.LIVE)
    }
}

/** A game's stream picker. */
@Composable
fun SportsStreamPicker(game: SportsGame, onPick: (SportsStreamMatch) -> Unit, onDismiss: () -> Unit) =
    SportsStreamPicker(SportsWatchTarget.game(game), onPick, onDismiss)

/**
 * The live channels that look like they carry an event (`/api/sports/game/{id}/streams`),
 * strongest match first. Picking one tunes it in the Live TV player; a single strong match of a
 * live event plays at once. "Search All Sources" widens the search past the admin's default
 * sources.
 */
@Composable
fun SportsStreamPicker(target: SportsWatchTarget, onPick: (SportsStreamMatch) -> Unit, onDismiss: () -> Unit) {
    var matches by remember { mutableStateOf<List<SportsStreamMatch>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var broad by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    LaunchedEffect(broad) {
        loading = true
        try {
            val groups = ApiClient.sportsStreams(target.id, broad, target.kind, target.parentId)
                .filter { (it.confidenceTier == "strong" || it.confidenceTier == "possible") && it.primary.channelUrl.isNotEmpty() }
            val feeds = groups.flatMap { listOf(it.primary) + it.alternatives.orEmpty() }
                .filter { it.channelUrl.isNotEmpty() }.distinctBy { it.key }
            // Strong matches first, otherwise in the server's order.
            matches = feeds.filter { it.isStrong } + feeds.filterNot { it.isStrong }
            error = if (matches.isEmpty()) "No channel looks like it carries this ${if (target.kind == null) "game" else "session"}." else null
            // One clear answer for a game in progress: no need to ask.
            val only = groups.singleOrNull()
            if (!broad && target.live && only != null && only.confidenceTier == "strong" &&
                only.alternatives.isNullOrEmpty()) {
                onPick(only.primary)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't search channels"
        } finally {
            loading = false
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
                        if (loading) "Looking for live channels" else if (matches.size == 1) "1 channel" else "${matches.size} channels",
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
                    items(matches, key = { it.key }) { match ->
                        ListItem(
                            selected = false,
                            onClick = { onPick(match) },
                            leadingContent = {
                                AsyncImage(model = match.channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit,
                                           modifier = Modifier.size(width = 64.dp, height = 36.dp))
                            },
                            headlineContent = { Text(match.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(details(match), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Text(if (match.isStrong) "Strong match" else "Possible match", fontSize = 12.sp,
                                     color = if (match.isStrong) Color(0xFF34C759) else SportsMuted)
                            },
                            modifier = if (match == matches.first()) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                    if (!broad) {
                        item {
                            ListItem(
                                selected = false, onClick = { broad = true },
                                headlineContent = { Text("Search All Sources") },
                                modifier = (if (matches.isEmpty()) Modifier.focusRequester(first) else Modifier).padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun details(match: SportsStreamMatch): String {
    val percent = match.confidence?.let { "${Math.round(it * 100)}%" }
    return listOfNotNull(match.sourceName, match.programTitle, match.matchReason, percent).filter { it.isNotEmpty() }.joinToString(" · ")
}
