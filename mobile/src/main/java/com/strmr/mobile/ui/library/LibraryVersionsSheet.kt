package com.strmr.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strmr.mobile.ui.components.SheetHeader
import com.strmr.tv.api.LibrarySource
import com.strmr.tv.api.StartPoint
import com.strmr.tv.library.LibraryVersion
import com.strmr.tv.ui.library.LibraryFileStart

/**
 * A movie's or episode's library copies as a sheet, in the server's order (local first; the
 * first is the one Play uses): where the copy lives, its quality, the file and its size. Opened
 * by Play, it ends with Stream Instead ([onStream]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryVersionsSheet(
    heading: String,
    versions: List<LibraryVersion>,
    onPick: (LibraryVersion) -> Unit,
    onDismiss: () -> Unit,
    onStream: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                SheetHeader(heading, if (versions.size == 1) "1 version in your libraries" else "${versions.size} versions in your libraries", null)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            if (versions.isEmpty()) item { Text("No library copies", Modifier.padding(24.dp)) }
            items(versions, key = { it.id }) { version ->
                LibraryVersionItem(version) {
                    onDismiss()
                    onPick(version)
                }
            }
            if (onStream != null) item {
                ListItem(
                    headlineContent = { Text("Stream Instead", fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(Icons.Outlined.CloudQueue, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onDismiss()
                        onStream()
                    },
                )
            }
        }
    }
}

/** One copy: its source badge and quality, then the file name, where it lives and its size. */
@Composable
fun LibraryVersionItem(version: LibraryVersion, onClick: () -> Unit) {
    ListItem(
        overlineContent = { Text(version.quality, fontWeight = FontWeight.Bold) },
        headlineContent = { Text(version.item.fileName ?: version.quality, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(version.origin, version.size).joinToString("  ·  "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { LibrarySourceBadge(version.source) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
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

/** Resume from where it was left, or Start Over: the choice before a partly watched copy or source plays. */
@Composable
fun ResumeDialog(heading: String, resumeAt: StartPoint, onPlay: (StartPoint?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = { Text("You stopped at ${resumeAt.label}.") },
        confirmButton = { TextButton(onClick = { onDismiss(); onPlay(resumeAt) }) { Text("Resume") } },
        dismissButton = { TextButton(onClick = { onDismiss(); onPlay(null) }) { Text("Start Over") } },
    )
}

/** Resume / Start Over for a library file left partway. */
@Composable
fun LibraryResumeDialog(start: LibraryFileStart, onDismiss: () -> Unit) {
    val at = start.resumeAt ?: return
    ResumeDialog(start.episode?.let { "${start.group.title} · ${it.code}" } ?: start.group.title, at, start::play, onDismiss)
}
