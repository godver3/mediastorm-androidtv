package com.strmr.mobile.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.tv.player.PlayerController
import com.strmr.tv.player.ProgrammeTimeline

/** White icons over the video, whatever the theme's content colour. */
private val OnVideo @Composable get() = IconButtonDefaults.iconButtonColors(contentColor = Color.White)

/**
 * Close, the title (and episode), a growing recording's REC capsule, then picture in picture,
 * Audio, Subtitles and Settings.
 */
@Composable
fun TopBar(
    controller: PlayerController,
    onExit: () -> Unit,
    onPictureInPicture: (() -> Unit)?,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = controller.request
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onExit, colors = OnVideo) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(request.displayTitle, color = Color.White, style = MaterialTheme.typography.titleMedium,
                 fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            request.displaySubtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // A recording still recording: its playlist grows as it plays.
        if (controller.session.growingRestart != null) LiveBadge("REC", Modifier.padding(horizontal = 8.dp))
        onPictureInPicture?.let { IconButton(onClick = it, colors = OnVideo) { Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture") } }
        IconButton(onClick = onAudio, colors = OnVideo) { Icon(Icons.Filled.Audiotrack, "Audio") }
        IconButton(onClick = onSubtitles, colors = OnVideo) { Icon(Icons.Filled.ClosedCaption, "Subtitles") }
        IconButton(onClick = onSettings, colors = OnVideo) { Icon(Icons.Filled.Settings, "Settings") }
    }
}

/** −10 s, play / pause (a spinner while it buffers) and +10 s; only play / pause where it can't seek. */
@Composable
fun CenterButtons(
    seekable: Boolean,
    playing: Boolean,
    loading: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (seekable) RoundButton(Icons.Filled.Replay10, "Back 10 seconds", 52, onBack)
        Box(contentAlignment = Alignment.Center) {
            RoundButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", 72, onPlayPause)
            if (loading) CircularProgressIndicator(Modifier.size(72.dp), color = Color.White, strokeWidth = 2.dp)
        }
        if (seekable) RoundButton(Icons.Filled.Forward10, "Forward 10 seconds", 52, onForward)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, size: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = OnVideo,
        modifier = Modifier.size(size.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape),
    ) { Icon(icon, contentDescription = label, modifier = Modifier.size((size * 0.6f).dp)) }
}

/**
 * The seek bar, or a live channel's LIVE capsule when it has no timeshift window or catch-up;
 * Start Over (catch-up) and Go Live (back to the edge) above it on the right.
 */
@Composable
fun BottomBar(
    controller: PlayerController,
    live: Boolean,
    timeshift: Boolean,
    programme: ProgrammeTimeline?,
    showGoLive: Boolean,
    showStartOver: Boolean,
    onScrubbing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        if (showGoLive || showStartOver) {
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chip = AssistChipDefaults.assistChipColors(labelColor = Color.White, leadingIconContentColor = Color.White)
                if (showStartOver) {
                    AssistChip(onClick = { controller.seekTo(0) }, label = { Text("Start Over") }, colors = chip,
                               leadingIcon = { Icon(Icons.Filled.Replay, null, Modifier.size(18.dp)) })
                }
                if (showGoLive) {
                    AssistChip(onClick = { controller.goLive() }, label = { Text("Go Live") }, colors = chip,
                               leadingIcon = { Icon(Icons.Filled.SkipNext, null, Modifier.size(18.dp)) })
                }
            }
        }
        if (live && !timeshift && programme == null) {
            LiveBadge(modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
        } else {
            SeekBar(controller, programme, live = live, onScrubbing = onScrubbing, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}
