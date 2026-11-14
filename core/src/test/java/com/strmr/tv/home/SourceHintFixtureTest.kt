package com.strmr.tv.home

import com.strmr.tv.api.SourceAvailability
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Optional exported decisions drive a labelled browser fixture, not a native screenshot. */
class SourceHintFixtureTest {
    @Serializable
    data class Decision(val name: String, val label: String?, val checkedLabel: String?, val description: String?, val kind: String?, val dimArtwork: Boolean)

    @Test fun exportsSyntheticDecisionsFromTheActualPolicy() {
        val now = Instant.parse("2026-11-14T15:00:00Z")
        val negative = SourceAvailability("not_found", now.minusSeconds(60).toString(), now.plusSeconds(3600).toString(), complete = true, reason = "reported_candidates")
        val cases = listOf(
            "Complete missing source" to negative,
            "Matching alternative" to negative.copy(state = "found", reason = "matching_candidates"),
            "Incomplete search" to negative.copy(state = "unknown", complete = false, reason = "search_incomplete"),
            "Expired source check" to negative.copy(expiresAt = now.toString()),
            "Reported audio track" to negative.copy(state = "unknown", complete = false, reason = "audio_track_unverified"),
            "Incomplete negative" to negative.copy(complete = false),
            "Future server state" to negative.copy(state = "future_state"),
            "Older server" to null,
        )
        val decisions = cases.map { (name, source) ->
            val hint = SourceHint.from(source, now)
            Decision(name, hint?.label, hint?.checkedLabel(Locale.UK, ZoneId.of("UTC")), hint?.description(Locale.UK, ZoneId.of("UTC")), hint?.kind?.name, hint?.dimArtwork == true)
        }
        assertEquals(listOf(true, false, false, false, false, false, false, false), decisions.map { it.dimArtwork })
        System.getenv("MEDIASTORM_SOURCE_HINT_ARTIFACTS")?.let { path ->
            File(path).apply { mkdirs() }.resolve("decisions.json").writeText(Json { prettyPrint = true }.encodeToString(decisions))
        }
    }
}
