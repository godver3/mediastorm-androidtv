package com.strmr.tv.home

import com.strmr.tv.api.SourceAvailability
import com.strmr.tv.api.Title
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourceHintTest {
    private val now = Instant.parse("2026-11-14T15:00:00Z")
    private val json = Json { ignoreUnknownKeys = true }
    private fun missing() = SourceAvailability("not_found", now.minusSeconds(60).toString(), now.plusSeconds(3600).toString(), listOf("Synthetic indexer"), true, "reported_candidates")
    private fun hint(value: SourceAvailability) = SourceHint.from(value, now)!!

    @Test fun manualCheckStatesRemainUnknownAndReadableWithExpiredPreviousEvidence() {
        val labels = mapOf("recheck_queued" to "Checking sources", "recheck_budget_exhausted" to "Source check limit reached", "recheck_timed_out" to "Source check timed out", "recheck_no_sources" to "No source checker available", "recheck_failed" to "Source check needs retry", "recheck_context_changed" to "Source check needs retry")
        for ((reason,label) in labels) for (state in listOf("unknown","found","not_found")) {
            val result=hint(missing().copy(state=state,reason=reason,expiresAt=now.minusSeconds(1).toString()))
            assertEquals(SourceHint.Kind.UNKNOWN,result.kind);assertFalse(result.dimArtwork);assertEquals(label,result.label)
        }
    }
    @Test fun completeFreshNegativeDimsOnlyArtwork() {
        for (reason in listOf("no_matching_candidates", "reported_candidates")) {
            val result = hint(missing().copy(reason = reason))
            assertEquals(SourceHint.Kind.MISSING, result.kind)
            assertTrue(result.dimArtwork)
            assertEquals("No matching source", result.label)
            assertTrue(result.description().contains("Open details"))
        }
    }
    @Test fun foundCandidateIsNotAPlaybackGuarantee() {
        val result = hint(missing().copy(state = "found", complete = false, reason = "matching_candidates"))
        assertEquals(SourceHint.Kind.CANDIDATE, result.kind)
        assertFalse(result.dimArtwork)
        assertTrue(result.description().contains("not proof of playback"))
    }
    @Test fun unknownAndFutureStateNamesNeverDim() {
        for (state in listOf("unknown", "partial", "future_state", "", "NOT_FOUND")) {
            val result = hint(missing().copy(state = state))
            assertEquals(SourceHint.Kind.UNKNOWN, result.kind)
            assertFalse(result.dimArtwork)
        }
    }
    @Test fun incompleteNegativeDoesNotEstablishAbsence() {
        assertFalse(hint(missing().copy(complete = false)).dimArtwork)
    }
    @Test fun errorQuotaAndUnconfirmedReasonsCannotBecomeNegatives() {
        for (reason in listOf(null, "search_incomplete", "quota_exceeded", "reports_changed", "audio_track_unverified", "audio_not_confirmed")) {
            assertEquals(SourceHint.Kind.UNKNOWN, hint(missing().copy(reason = reason)).kind)
        }
    }
    @Test fun expiryEqualityAndPastTimestampsRemoveDimming() {
        for (time in listOf(now, now.minusSeconds(1))) {
            val result = hint(missing().copy(expiresAt = time.toString()))
            assertFalse(result.dimArtwork)
            assertEquals("Source check expired", result.label)
        }
    }
    @Test fun missingAndMalformedDatesStayUnknown() {
        for (time in listOf(null, "not a date", "2026-11-14")) {
            assertFalse(hint(missing().copy(checkedAt = time)).dimArtwork)
            assertFalse(hint(missing().copy(expiresAt = time)).dimArtwork)
        }
    }
    @Test fun futureCheckAndInvertedWindowCannotProveAbsence() {
        assertFalse(hint(missing().copy(checkedAt = now.plusSeconds(1).toString())).dimArtwork)
        assertFalse(hint(missing().copy(checkedAt = now.toString(), expiresAt = now.minusSeconds(1).toString())).dimArtwork)
    }
    @Test fun excessiveFreshnessWindowIsNotTrusted() {
        assertFalse(hint(missing().copy(expiresAt = now.plusSeconds(25 * 3600).toString())).dimArtwork)
        assertTrue(hint(missing().copy(checkedAt = now.toString(), expiresAt = now.plusSeconds(24 * 3600).toString())).dimArtwork)
    }
    @Test fun rfc3339OffsetsAndFractionsUseTheSameInstant() {
        val result = hint(missing().copy(checkedAt = "2026-11-14T15:59:00.123456789+01:00"))
        assertTrue(result.dimArtwork)
        assertEquals(Instant.parse("2026-11-14T14:59:00.123456789Z"), result.checkedAt)
        assertTrue(result.checkedLabel(Locale.GERMANY, ZoneId.of("Europe/Berlin"))!!.contains("15:59"))
    }
    @Test fun extremeDatesDoNotOverflowTimersOrFormatting() {
        val value = missing().copy(checkedAt = "-1000000000-01-01T00:00:00Z", expiresAt = "+1000000000-12-31T23:59:59.999999999Z")
        val result = hint(value)
        assertFalse(result.dimArtwork)
        result.description()
        assertEquals(60_000L, SourceHint.nextUpdateMillis(value, now))
    }
    @Test fun legacyAndNullHintsLeaveExistingCardsAlone() {
        for (payload in listOf("", ",\"sourceAvailability\":null")) {
            val title = json.decodeFromString<Title>("""{"id":"movie","name":"Movie","cardSubtitle":"Legacy context"$payload}""")
            val card = HomeCard.from(title)
            assertNull(card.sourceAvailability)
            assertNull(SourceHint.from(card.sourceAvailability, now))
            assertEquals("Legacy context", card.subtitle)
        }
    }
    @Test fun malformedOptionalHintsDoNotDiscardTitles() {
        for (raw in listOf("[]", "42", "\"bad hint\"", "{}", "{\"state\":[],\"complete\":true}", "{\"state\":\"not_found\",\"complete\":\"true\"}")) {
            val title = json.decodeFromString<Title>("""{"id":"movie","name":"Movie","sourceAvailability":$raw}""")
            assertEquals("movie", title.id)
            assertFalse(SourceHint.from(title.sourceAvailability, now)!!.dimArtwork)
        }
    }
    @Test fun structuredHintRoundTripsAndIgnoresNewFields() {
        val original = missing()
        assertEquals(original, json.decodeFromString<SourceAvailability>(json.encodeToString(original)))
        val title = json.decodeFromString<Title>("""{"id":"movie","name":"Movie","sourceAvailability":${json.encodeToString(original).dropLast(1)},"futureField":true}}""")
        assertEquals(original, title.sourceAvailability)
    }
    @Test fun quotedCompletenessDoesNotAuthorizeMissingState() {
        val payload = json.encodeToString(missing()).replace("\"complete\":true", "\"complete\":\"true\"")
        val decoded = json.decodeFromString<SourceAvailability>(payload)
        assertFalse(decoded.complete)
        assertFalse(hint(decoded).dimArtwork)
    }
    @Test fun titleAndRankedCardRetainHintAndDetailsAction() {
        val source = missing()
        val card = HomeCard.from(Title("movie", "Movie", sourceAvailability = source)).ranked(2)
        assertEquals(source, card.sourceAvailability)
        assertEquals("movie", card.target!!.titleId)
        assertEquals("#2", card.badge!!.text)
        assertEquals(HomeLink.Details(card.target!!), card.link)
        assertTrue(hint(card.sourceAvailability!!).dimArtwork)
    }
    @Test fun watchlistSerializationAndBrowseMappingKeepSourceContext() {
        val item = json.decodeFromString<WatchlistItem>("""{"id":"movie","name":"Movie","cardSubtitle":"No matching source in checked indexers · Synthetic chart","forceTitleOverlay":true,"sourceAvailability":${json.encodeToString(missing())}}""")
        val card = HomeCard.from(BrowseEntry.ItemEntry(item))
        assertEquals(missing(), card.sourceAvailability)
        assertEquals("Synthetic chart", card.subtitle)
        assertTrue(card.forceTitleOverlay)
        assertEquals("movie", card.target!!.titleId)
    }
    @Test fun transientServerTextDoesNotOutliveItsHint() {
        for (prefix in listOf("Source candidate found", "No matching source in checked indexers")) {
            val card = HomeCard.from(Title("movie", "Movie", year = 2024, cardSubtitle = "$prefix · Netflix #4", sourceAvailability = missing()))
            assertEquals("Netflix #4", card.subtitle)
            val plain = HomeCard.from(Title("movie", "Movie", year = 2024, cardSubtitle = prefix, sourceAvailability = missing()))
            assertEquals("2024 · Movie", plain.subtitle)
        }
    }
    @Test fun optionalBadgesDoNotRemoveSourceEvidenceOrChangeLibraryCards() {
        val card = HomeCard.from(Title("movie", "Movie", sourceAvailability = missing()))
        assertTrue(card.visibleBadges(BadgeVisibility()).isEmpty())
        assertEquals(missing(), card.sourceAvailability)
        val library = HomeCard("library", HomeLink.Action(HomeAction.SURPRISE), "Library", "Local")
        assertNull(library.sourceAvailability)
    }
    @Test fun singleSeriesCardDoesNotCreateAnEpisodeReference() {
        val card = HomeCard.from(Title("series", "Series", mediaType = "series", sourceAvailability = missing()))
        assertNull(card.episode)
        assertEquals("series", card.target!!.titleId)
        assertFalse(card.target!!.isMovie)
    }
    @Test fun expiryIsObservedWhileTheCardRemainsVisible() = runTest {
        val source = missing().copy(expiresAt = now.plusMillis(1000).toString())
        val states = async { sourceHintFlow(source) { now.plusMillis(testScheduler.currentTime) }.take(2).toList() }
        runCurrent()
        assertFalse(states.isCompleted)
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf(SourceHint.Kind.MISSING, SourceHint.Kind.UNKNOWN), states.await().map { it.kind })
    }
    @Test fun disposingObservationCancelsItsTimer() = runTest {
        var ticks = 0
        val observer = async { sourceHintFlow(missing()) { ticks++; now }.toList() }
        runCurrent(); assertEquals(1, ticks)
        observer.cancel(); runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(1, ticks)
    }
    @Test fun timerWaitsAreBoundedAndRecheckFutureChecks() {
        assertEquals(60_000L, SourceHint.nextUpdateMillis(missing(), now))
        assertEquals(1L, SourceHint.nextUpdateMillis(missing().copy(expiresAt = now.plusNanos(1).toString()), now))
        assertEquals(500L, SourceHint.nextUpdateMillis(missing().copy(checkedAt = now.plusMillis(500).toString()), now))
    }
}
