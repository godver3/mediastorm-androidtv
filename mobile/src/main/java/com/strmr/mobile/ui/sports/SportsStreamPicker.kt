package com.strmr.mobile.ui.sports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsStreamMatch
import com.strmr.tv.ui.sports.SportsStreamSearch
import com.strmr.tv.ui.sports.SportsWatchTarget

private val StrongGreen = Color(0xFF34C759)

/** A game's stream picker. */
@Composable
fun SportsStreamPicker(game: SportsGame, onPick: (SportsStreamMatch) -> Unit, onDismiss: () -> Unit) =
    SportsStreamPicker(SportsWatchTarget.game(game), onPick, onDismiss)

/**
 * Watch Live / Find Streams as a bottom sheet: the live channels that look like they carry the
 * event, strongest first, each Stremio addon channel's streams listed ([SportsStreamSearch]).
 * Picking one tunes it in the live player; a single strong match of a live event plays at
 * once. "Search All Sources" widens the search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsStreamPicker(target: SportsWatchTarget, onPick: (SportsStreamMatch) -> Unit, onDismiss: () -> Unit) {
    val search = remember(target) { SportsStreamSearch(target) }
    val pick by rememberUpdatedState(onPick)
    LaunchedEffect(search.broad) { search.run(pick) }
    val rows = search.rows
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(target.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (search.loading) "Looking for live channels" else if (rows.size == 1) "1 stream" else "${rows.size} streams",
                         color = SportsMuted)
                }
            }
            if (search.loading) {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(if (search.broad) "Searching all sources…" else "Searching channels…", color = SportsMuted)
                    }
                }
            } else {
                search.error?.let { item { Text(it, color = SportsMuted, modifier = Modifier.padding(16.dp)) } }
                items(rows, key = { it.key }) { match ->
                    ListItem(
                        leadingContent = {
                            AsyncImage(model = match.channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit,
                                       modifier = Modifier.size(width = 56.dp, height = 32.dp))
                        },
                        headlineContent = { Text(match.rowTitle, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(SportsStreamSearch.details(match), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (match.isStrong) "Strong match" else "Possible match", fontSize = 12.sp,
                                     color = if (match.isStrong) StrongGreen else SportsMuted)
                                if (match.loadingStreams) Text("Loading streams…", fontSize = 12.sp, color = SportsMuted)
                            }
                        },
                        colors = clear,
                        modifier = Modifier.clickable { onPick(match) },
                    )
                }
                if (!search.broad) {
                    item {
                        ListItem(
                            headlineContent = { Text("Search All Sources") },
                            leadingContent = { Icon(Icons.Filled.TravelExplore, null) },
                            colors = clear,
                            modifier = Modifier.clickable { search.broad = true },
                        )
                    }
                }
            }
        }
    }
}
