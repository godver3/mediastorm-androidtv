package com.strmr.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStore
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice

/** A channel's logo fitted in a box; without one (or when it fails), the channel's name or a TV symbol. */
@Composable
fun ChannelLogo(channel: LiveChannel, width: Dp, height: Dp, fontSize: TextUnit, namesChannel: Boolean = true) {
    val fallback = @Composable {
        Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
            if (namesChannel) {
                Text(channel.name, fontSize = fontSize, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                     maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Icon(AppIcons.LiveTv, contentDescription = null, tint = Color.White.copy(alpha = 0.35f))
            }
        }
    }
    val logo = channel.logoUrl
    if (logo == null) {
        fallback()
    } else {
        SubcomposeAsyncImage(
            model = logo, contentDescription = channel.name, contentScale = ContentScale.Fit,
            error = { fallback() }, modifier = Modifier.size(width, height),
        )
    }
}

/** Long-press actions shared by channel cards and guide cells: favorite and hide. */
@Composable
fun ChannelMenu(store: LiveStore, channel: LiveChannel, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = channel.name,
        choices = listOf(
            DialogChoice(if (channel.id in store.favorites) "Remove from Favorites" else "Add to Favorites") { store.toggleFavorite(channel) },
            DialogChoice(if (channel.id in store.hidden) "Show Channel" else "Hide Channel") { store.toggleHidden(channel) },
            DialogChoice("Cancel") {},
        ),
        onDismiss = onDismiss,
    )
}

/** "9:00–10:30 PM" in the device's clock format. */
fun timeRange(startMs: Long, endMs: Long): String = "${clock(startMs)}–${clock(endMs)}"

fun clock(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

/** A thin bar for how far into its programme a channel is. */
@Composable
fun AiringProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(com.strmr.tv.ui.Accent))
    }
}
