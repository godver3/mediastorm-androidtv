package com.strmr.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi

/** The Info panel's stream-details rows, label / value; a row whose value is unknown is left out. */
fun streamInfoRows(session: PlayerSession, video: Format?, audio: Format?): List<Pair<String, String>> {
    val v = video?.facts()
    val a = audio?.facts()
    return listOfNotNull(
        session.releaseName?.takeIf { it.isNotBlank() && !session.isLive }?.let { "File" to it },
        v?.let(StreamInfo::video)?.let { "Video" to it },
        v?.let { StreamInfo.dynamicRange(it, session.sourceDolbyVision, session.releaseName, session.sourceHdr10) }?.let { "Dynamic range" to it },
        a?.let(StreamInfo::audio)?.let { "Audio" to it },
        session.sourceLabel?.let { "Source" to it },
    )
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
