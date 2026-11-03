package com.strmr.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.api.LibrarySource
import com.strmr.tv.library.LibraryVersion
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.BrandBackground

/**
 * Full-screen list of a movie's or episode's library copies in the server's order (local first; the
 * first is the one autoplay uses), laid out like the source list: where the copy lives, its quality,
 * the file and its size. Opened by Play, it ends with Stream Instead ([onStream]).
 */
@Composable
fun LibraryVersionsDialog(
    heading: String,
    versions: List<LibraryVersion>,
    onPick: (LibraryVersion) -> Unit,
    onDismiss: () -> Unit,
    onStream: (() -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(BrandBackground), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 760.dp),
                contentPadding = PaddingValues(vertical = 36.dp, horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text(heading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(if (versions.size == 1) "1 version in your libraries" else "${versions.size} versions in your libraries",
                             color = Color.White.copy(alpha = 0.6f))
                    }
                }
                if (versions.isEmpty()) item { Text("No library copies", color = Color.White.copy(alpha = 0.6f)) }
                itemsIndexed(versions, key = { _, v -> v.id }) { index, version ->
                    LibraryVersionItem(version, if (index == 0) Modifier.focusRequester(first) else Modifier) {
                        onDismiss()
                        onPick(version)
                    }
                }
                if (onStream != null) item {
                    ListItem(
                        selected = false,
                        onClick = {
                            onDismiss()
                            onStream()
                        },
                        headlineContent = { Text("Stream Instead", fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(AppIcons.CloudDownload, contentDescription = null) },
                    )
                }
            }
        }
    }
}

/** One copy: its source badge and quality, then the file name, where it lives and its size. */
@Composable
fun LibraryVersionItem(version: LibraryVersion, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        overlineContent = { Text(version.quality, fontWeight = FontWeight.Bold) },
        headlineContent = { Text(version.item.fileName ?: version.quality, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(listOfNotNull(version.origin, version.size).joinToString("  ·  "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { LibrarySourceBadge(version.source) },
        modifier = modifier,
    )
}

/** "Local" / "Plex" / "Jellyfin" in the Expo badge colours (green, amber, violet). */
@Composable
fun LibrarySourceBadge(source: LibrarySource) {
    val color = when (source) {
        LibrarySource.LOCAL -> Color(0xFF2ECC71)
        LibrarySource.PLEX -> Color(0xFFFF9F1A)
        LibrarySource.JELLYFIN -> Color(0xFF6C5CE7)
    }
    Text(
        source.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
