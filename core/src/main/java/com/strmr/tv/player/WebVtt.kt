package com.strmr.tv.player

/** One WebVTT cue, its times in milliseconds of the video and its text without markup. */
data class VttCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * The WebVTT the server returns for downloaded and translated subtitles, read into plain
 * cues for [SubtitleOverlay] (which draws text in the device's style, so styling tags and
 * cue settings are dropped).
 */
object WebVtt {
    private val fullTiming = Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")
    private val shortTiming = Regex("\\d{2}:\\d{2}\\.\\d{3}\\s*-->\\s*\\d{2}:\\d{2}\\.\\d{3}")
    private val timestamp = Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{2})[.,](\\d{1,3})")
    private val tag = Regex("<[^>]*>")
    private val blankLine = Regex("\\n[ \\t]*\\n")
    private val entities = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'",
        "&nbsp;" to " ", "&lrm;" to "‎", "&rlm;" to "‏",
    )

    /** Cue count as the Expo app counts it while waiting for a download or a translation. */
    fun countCues(text: String): Int {
        if (text.isEmpty()) return 0
        val full = fullTiming.findAll(text).count()
        return if (full > 0) full else shortTiming.findAll(text).count()
    }

    fun parse(text: String): List<VttCue> {
        val cues = mutableListOf<VttCue>()
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').removePrefix("﻿")
        for (block in normalized.split(blankLine)) {
            val lines = block.trim('\n').lines()
            val timingAt = lines.indexOfFirst { "-->" in it }
            if (timingAt < 0) continue // header, NOTE, STYLE, REGION
            val (startText, endText) = lines[timingAt].split("-->", limit = 2).map { it.trim() }
            val start = millis(startText) ?: continue
            val end = millis(endText) ?: continue
            val body = lines.drop(timingAt + 1).joinToString("\n") { clean(it) }.trim()
            if (body.isEmpty() || end <= start) continue
            cues += VttCue(start, end, body)
        }
        cues.sortBy { it.startMs }
        return cues
    }

    /** The cues on screen at `positionMs`, in start order. */
    fun active(cues: List<VttCue>, positionMs: Long): List<VttCue> =
        cues.filter { positionMs >= it.startMs && positionMs < it.endMs }

    /** `hh:mm:ss.ttt` or `mm:ss.ttt` at the start of `text` (cue settings may follow). */
    private fun millis(text: String): Long? {
        val m = timestamp.find(text) ?: return null
        if (m.range.first != 0) return null
        val hours = m.groupValues[1].ifEmpty { "0" }.toLong()
        val minutes = m.groupValues[2].toLong()
        val seconds = m.groupValues[3].toLong()
        val fraction = m.groupValues[4].padEnd(3, '0').toLong()
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + fraction
    }

    private fun clean(line: String): String {
        var out = line.replace(tag, "")
        entities.forEach { (k, v) -> out = out.replace(k, v) }
        return out.trim()
    }
}
