package com.strmr.tv.home

import com.strmr.tv.api.SourceAvailability
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A presentation decision only. Missing hints never disable opening a title. */
data class SourceHint(
    val kind: Kind,
    val label: String,
    val checkedAt: Instant?,
    val expiresAt: Instant?,
) {
    enum class Kind { CANDIDATE, LOCAL, MISSING, UNKNOWN }
    val dimArtwork: Boolean get() = kind == Kind.MISSING

    fun checkedLabel(locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String? =
        checkedAt?.let { runCatching { "Checked " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(it) }.getOrNull() }

    fun description(locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        listOfNotNull(label, checkedLabel(locale, zone), when (kind) {
            Kind.CANDIDATE -> "A search candidate is not proof of playback."
            Kind.LOCAL -> "The local file and requested audio were checked. Playback can still fail."
            Kind.MISSING -> "No matching source was found in the completed check. Open details for other options."
            Kind.UNKNOWN -> "A missing source has not been established. Open details to choose a source."
        }).joinToString(". ")

    companion object {
        fun from(value: SourceAvailability?, now: Instant = Instant.now()): SourceHint? {
            if (value == null) return null
            val checked = parse(value.checkedAt)
            val expiry = parse(value.expiresAt)
            val valid = checked != null && expiry != null && !checked.isAfter(now) && expiry.isAfter(checked) && expiry.isAfter(now) && Duration.between(checked, expiry) <= Duration.ofHours(24)
            val kind = when {
                value.reason?.startsWith("recheck_") == true -> Kind.UNKNOWN
                !valid -> Kind.UNKNOWN
                value.sourceType != null || value.reason == "local_file_checked" ->
                    if (value.state == "found" && value.sourceType == "local" && value.reason == "local_file_checked" && value.providers.isEmpty() && Duration.between(checked, expiry) <= Duration.ofMinutes(2)) Kind.LOCAL else Kind.UNKNOWN
                value.state == "not_found" && value.complete && value.reason in setOf("no_matching_candidates", "reported_candidates") -> Kind.MISSING
                value.state == "found" -> Kind.CANDIDATE
                else -> Kind.UNKNOWN
            }
            val label = when (kind) {
                Kind.MISSING -> "No matching source"
                Kind.CANDIDATE -> "Source candidate found"
                Kind.LOCAL -> "Local file checked"
                Kind.UNKNOWN -> when {
                    value.reason == "audio_track_unverified" -> "Audio track needs checking"
                    value.reason == "audio_not_confirmed" -> "Audio language unconfirmed"
                    value.reason == "not_checked" -> "Source not checked"
                    value.reason == "recheck_queued" -> "Checking sources"
                    value.reason == "recheck_budget_exhausted" -> "Source check limit reached"
                    value.reason == "recheck_timed_out" -> "Source check timed out"
                    value.reason == "recheck_no_sources" -> "No source checker available"
                    value.reason in setOf("recheck_failed", "recheck_context_changed") -> "Source check needs retry"
                    expiry != null && !expiry.isAfter(now) -> "Source check expired"
                    value.reason == "local_source_unconfirmed" -> "Local source needs checking"
                    value.reason == "catalogue_refresh_required" -> "Reload catalogue to check"
                    else -> "Source status unknown"
                }
            }
            return SourceHint(kind, label, checked?.takeUnless { it.isAfter(now) }, expiry)
        }

        private fun parse(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

        /** Wake at the normal expiry boundary and periodically recheck wall-clock changes. */
        fun nextUpdateMillis(value: SourceAvailability, now: Instant): Long {
            val next = listOfNotNull(parse(value.checkedAt), parse(value.expiresAt)).filter { it.isAfter(now) }.minOrNull()
            return next?.let {
                val remaining = Duration.between(now, it)
                if (remaining.seconds >= 60) 60_000L else remaining.toMillis().coerceAtLeast(1L)
            } ?: 60_000L
        }

        /** Remove the server's transient hint while preserving chart/list provenance. */
        fun contextSubtitle(raw: String?, value: SourceAvailability?, fallback: String): String {
            var text = raw?.trim().orEmpty()
            if (value != null) {
                for (prefix in listOf("Source candidate found", "No matching source in checked indexers")) {
                    text = if (text == prefix) "" else text.removePrefix("$prefix · ")
                }
            }
            return text.ifBlank { fallback }
        }
    }
}
