package com.strmr.tv.live

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Expo TV Recordings filters (`TVRecordings.tsx`), in its order. */
enum class RecordingFilter(val label: String) {
    ALL("All"), UPCOMING("Upcoming"), RECORDING("Recording"), COMPLETED("Completed"), HISTORY("History");

    fun matches(r: Recording): Boolean = when (this) {
        ALL -> true
        UPCOMING -> r.status == "pending"
        RECORDING -> r.status == "starting" || r.status == "running"
        COMPLETED -> r.status == "completed"
        HISTORY -> r.status == "failed" || r.status == "cancelled"
    }
}

/** What the Recordings page shows and offers for a recording, as Expo's TV page and confirmation modal do. */
object RecordingRules {
    fun statusLabel(status: String): String = when (status) {
        "pending" -> "Scheduled"
        "starting" -> "Starting"
        "running" -> "Recording"
        "completed" -> "Completed"
        "cancelled" -> "Cancelled"
        "failed" -> "Failed"
        else -> status.replaceFirstChar { it.uppercase() }
    }

    fun isRecording(r: Recording) = r.status == "running"

    /** A saved file that has started (finished, stopped, failed part way, or still growing). */
    fun canPlay(r: Recording) = !r.outputPath.isNullOrBlank() && r.status in setOf("completed", "running", "cancelled", "failed")

    fun canCancel(r: Recording) = r.status in setOf("pending", "starting", "running")

    /** A rule's upcoming recording is cancelled, not deleted (the rule would schedule it again). */
    fun canDelete(r: Recording) = !(!r.ruleId.isNullOrEmpty() && r.status == "pending")

    /** Rule housekeeping messages aren't failures the user needs to see. */
    fun visibleError(error: String?): String? =
        error?.takeIf { it.isNotBlank() && it != "recording rule paused" && !it.startsWith("cancelled because the recording rule") }

    private val filenameTitle = listOf(
        Regex("^.+ - \\d{4}-\\d{2}-\\d{2} \\d{4}-\\d{4} - (.+)$", RegexOption.IGNORE_CASE),
        Regex("^.+ - \\d{4}-\\d{2}-\\d{2} \\d{4} - (.+)$", RegexOption.IGNORE_CASE),
    )

    private fun clean(value: String?) = value.orEmpty().trim().replace('+', ' ').replace(Regex("\\s+"), " ").trim()

    /**
     * The programme's title; a recording titled after its channel falls back to the title in
     * its file name ("Channel - 2026-10-22 1900-2000 - Title.ts"), as Expo's `resolveRecordingTitle`.
     */
    fun title(r: Recording): String {
        val raw = clean(r.title)
        val channel = clean(r.channelName).ifEmpty { "Recording" }
        if (raw.isNotEmpty() && raw != channel) return raw
        val file = r.outputPath?.trim()?.substringAfterLast('/')?.replace(Regex("\\.[^.]+$"), "")?.trim().orEmpty()
        if (file.isNotEmpty()) {
            for (pattern in filenameTitle) {
                val derived = clean(pattern.find(file)?.groupValues?.get(1))
                if (derived.isNotEmpty() && derived != channel) return derived
            }
        }
        return raw.ifEmpty { channel }
    }

    /** The server's times carry its zone offset ("…-06:00"), which older `Instant.parse` rejects. */
    fun instant(value: String?): Instant? = value?.takeIf { it.isNotBlank() }?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() ?: runCatching { Instant.parse(it) }.getOrNull()
    }

    private val dateTime = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

    /** "Oct 22, 7:05 PM" in the device's zone (Expo's short month / day / time). */
    fun dateTime(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
        instant(value)?.let { dateTime.format(it.atZone(zone)) } ?: value

    /** "45m", "1h", "1h 30m"; empty when the times are unusable. */
    fun duration(start: String, end: String): String {
        val s = instant(start) ?: return ""
        val e = instant(end) ?: return ""
        if (!e.isAfter(s)) return ""
        val minutes = Math.round((e.toEpochMilli() - s.toEpochMilli()) / 60_000.0)
        if (minutes < 60) return "${minutes}m"
        val rest = minutes % 60
        return if (rest > 0) "${minutes / 60}h ${rest}m" else "${minutes / 60}h"
    }

    /** "812 MB", "1.4 GB"; null before anything is saved. */
    fun size(bytes: Long?): String? {
        val b = bytes?.takeIf { it > 0 } ?: return null
        val mb = b / 1_000_000.0
        return if (mb < 1000) "${Math.round(mb).coerceAtLeast(1)} MB" else String.format(Locale.US, "%.1f GB", mb / 1000)
    }

    /** Expo's `RecordingConfirmationModal`: title, message and the confirming button. */
    data class Confirmation(val title: String, val message: String, val confirm: String)

    fun confirmation(r: Recording, delete: Boolean): Confirmation {
        val name = title(r)
        return when {
            delete -> Confirmation("Delete recording?", "Delete $name and its saved video? This cannot be undone.", "Delete Recording")
            isRecording(r) -> Confirmation("Stop recording?", "Stop recording $name? Video captured so far will be kept.", "Stop Recording")
            else -> Confirmation("Cancel scheduled recording?", "Cancel $name? This recording will not start.", "Cancel Recording")
        }
    }
}
