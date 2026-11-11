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
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsStreamMatch
import com.strmr.tv.ui.pageBackground
import com.strmr.tv.sports.SportsStreamOptions

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
    val search = remember(target) { SportsStreamSearch(target) }
    val loading = search.loading
    val broad = search.broad
    val error = search.error
    val rows = search.rows
    val first = remember { FocusRequester() }
    val pick by rememberUpdatedState(onPick)

    // Leaving the picker (or searching again) cancels the lookups still running.
    LaunchedEffect(broad) { search.run(pick) }
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
                            headlineContent = { Text(match.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Column {
                                    Text(SportsStreamSearch.details(match), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                selected = false, onClick = { search.broad = true },
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
