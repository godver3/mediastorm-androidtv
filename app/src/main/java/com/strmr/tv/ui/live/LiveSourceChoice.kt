package com.strmr.tv.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.live.LiveSourceChoice
import com.strmr.tv.live.LiveStremioStream
import com.strmr.tv.ui.pageBackground

/**
 * The streams of one Stremio channel, in the addon's order (Expo's "Choose stream source"),
 * the first focused. Picking one tunes the channel on that stream.
 */
@Composable
fun LiveSourceChoiceDialog(choice: LiveSourceChoice, onDismiss: () -> Unit, onPick: (LiveStremioStream) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().pageBackground()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 80.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(choice.channel, 96.dp, 54.dp, 14.sp, namesChannel = false)
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(choice.channel.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text("Choose stream source", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                itemsIndexed(choice.streams, key = { _, s -> s.index }) { i, stream ->
                    ListItem(
                        selected = false,
                        onClick = { onPick(stream) },
                        leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        // The label is the whole release line (Expo shows it in full); wrap rather than truncate.
                        headlineContent = { Text(stream.label, maxLines = 3) },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}
