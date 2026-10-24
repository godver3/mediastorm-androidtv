package com.strmr.tv.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** The Info panel's stream-details rows, label / value; a row whose value is unknown is left out. */
fun streamInfoRows(session: PlayerSession, video: Format?, audio: Format?): List<Pair<String, String>> {
    val v = video?.facts()
    val a = audio?.facts()
    return listOfNotNull(
        session.releaseName?.takeIf { it.isNotBlank() && !session.isLive }?.let { "File" to it },
        v?.let(StreamInfo::video)?.let { "Video" to it },
        v?.let { StreamInfo.dynamicRange(it, session.sourceDolbyVision, session.releaseName) }?.let { "Dynamic range" to it },
        a?.let(StreamInfo::audio)?.let { "Audio" to it },
        session.sourceLabel?.let { "Source" to it },
    )
}

/** Live: the stream's formats arrive after the panel opens, and audio changes with the track. */
@Composable
fun StreamInfoRows(controller: PlayerController, modifier: Modifier = Modifier) {
    val video by controller.videoFormat.collectAsState()
    val audio by controller.audioFormat.collectAsState()
    val rows = streamInfoRows(controller.session, video, audio)
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).height(1.dp).background(Color.White.copy(alpha = 0.15f)))
        rows.forEach { (label, value) ->
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f),
                     modifier = Modifier.width(140.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f),
                     maxLines = if (label == "File") 2 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(UnstableApi::class)
internal fun Format.facts() = TrackFacts(
    mime = sampleMimeType,
    codecs = codecs,
    width = width.takeIf { it != Format.NO_VALUE },
    height = height.takeIf { it != Format.NO_VALUE },
    frameRate = frameRate.takeIf { it != Format.NO_VALUE.toFloat() },
    bitrate = (averageBitrate.takeIf { it > 0 } ?: bitrate.takeIf { it > 0 } ?: peakBitrate.takeIf { it > 0 }),
    colorTransfer = colorInfo?.colorTransfer?.takeIf { it != Format.NO_VALUE },
    channels = channelCount.takeIf { it != Format.NO_VALUE },
    language = language,
    label = label,
)
