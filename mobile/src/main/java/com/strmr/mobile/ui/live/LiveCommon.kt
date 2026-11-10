package com.strmr.mobile.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.live.EpgProgram
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** The recording red (a running recording, LIVE), the same on every screen. */
val RecordingRed = Color(0xFFFF453A)
val FavoriteYellow = Color(0xFFFFD60A)

/** A channel's logo fitted in a box; without one (or when it fails), the channel's name or a TV symbol. */
@Composable
fun ChannelLogo(channel: LiveChannel, width: Dp, height: Dp, namesChannel: Boolean = true) {
    val fallback = @Composable {
        Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
            if (namesChannel) {
                Text(channel.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                     maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Icon(Icons.Filled.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val logo = channel.logoUrl
    if (logo == null) fallback()
    else SubcomposeAsyncImage(model = logo, contentDescription = channel.name, contentScale = ContentScale.Fit,
                              error = { fallback() }, modifier = Modifier.size(width, height))
}

/** A guide cell's or row's mark: a red dot while recording, a clock when scheduled. */
@Composable
fun RecordingBadge(recording: Recording, size: Dp = 12.dp) {
    val running = recording.status == "running"
    Icon(
        if (running) Icons.Filled.FiberManualRecord else Icons.Filled.Schedule,
        contentDescription = RecordingRules.statusLabel(recording.status),
        tint = if (running) RecordingRed else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(size),
    )
}

/** A small capsule label: LIVE, NEW, CATCH-UP. */
@Composable
fun Pill(text: String, container: Color, content: Color) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Black, color = content,
         modifier = Modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** "NEW" on a programme the guide flags as a first airing. */
@Composable
fun NewTag(prominent: Boolean = false) {
    if (prominent) Pill("NEW", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
    else Text("NEW", fontSize = 9.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
}

/** "9:00 PM" in the device's clock format. */
fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** "9:00–10:30 PM". */
fun timeRange(startMs: Long, endMs: Long): String = "${clock(startMs)}–${clock(endMs)}"

/** "Tue 4" for the guide's corner. */
fun shortDay(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.dayOfMonth}"
}

/** A programme's times, how far off, episode, rerun and category, as the TV guide's info panel. */
fun programDetails(p: EpgProgram, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(p.startMs).atZone(zone).toLocalDate()
    val range = timeRange(p.startMs, p.endMs)
    val parts = mutableListOf(
        if (day == LocalDate.now(zone)) range
        else "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $range",
    )
    parts += when {
        p.isAiring(now) -> "${(p.endMs - now) / 60_000} min left"
        p.startMs > now -> "in ${(p.startMs - now) / 60_000} min"
        else -> "${(now - p.startMs) / 3_600_000}h ago"
    }
    p.episode?.takeIf { it.isNotEmpty() }?.let { parts += it }
    if (p.isRerun && !p.isNew) parts += "Rerun"
    p.categories?.firstOrNull()?.let { parts += it }
    return parts.joinToString(" · ")
}
