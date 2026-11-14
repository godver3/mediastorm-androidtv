package com.strmr.tv.home

import com.strmr.tv.api.SourceAvailability
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalSourceHintTest {
    private val now = Instant.parse("2026-11-14T19:00:00Z")
    private fun local() = SourceAvailability("found", now.minusSeconds(10).toString(), now.plusSeconds(110).toString(), reason="local_file_checked", sourceType="local")
    private fun hint(source: SourceAvailability) = SourceHint.from(source,now)!!
    @Test fun checkedLocalFileHasDistinctReadableStatus() {
        val h = hint(local());assertEquals(SourceHint.Kind.LOCAL,h.kind);assertEquals("Local file checked",h.label);assertFalse(h.dimArtwork)
        assertTrue(h.description().contains("requested audio"));assertFalse(h.description().contains("search candidate"))
    }
    @Test fun sourceTypeSurvivesJsonRoundTrip() {
        val json = Json { ignoreUnknownKeys=true };val value=json.decodeFromString<SourceAvailability>(json.encodeToString(local()))
        assertEquals("local",value.sourceType);assertEquals(SourceHint.Kind.LOCAL,hint(value).kind)
    }
    @Test fun incompleteOrContradictoryLocalMarkersRemainUnknown() {
        for (value in listOf(local().copy(state="not_found",complete=true),local().copy(sourceType=null),local().copy(reason="matching_candidates"),local().copy(providers=listOf("Synthetic indexer")),local().copy(sourceType="future"))) {
            assertEquals(SourceHint.Kind.UNKNOWN,hint(value).kind);assertFalse(hint(value).dimArtwork)
        }
    }
    @Test fun localProofCannotUseRemoteTwentyFourHourFreshness() {
        assertEquals(SourceHint.Kind.UNKNOWN,hint(local().copy(expiresAt=now.plusSeconds(111).toString())).kind)
    }
    @Test fun expiredAndFutureLocalEvidenceCannotStayPositive() {
        assertEquals(SourceHint.Kind.UNKNOWN,hint(local().copy(expiresAt=now.minusSeconds(1).toString())).kind)
        assertEquals(SourceHint.Kind.UNKNOWN,hint(local().copy(checkedAt=now.plusSeconds(1).toString())).kind)
    }
    @Test fun malformedOptionalLocalFieldDoesNotClaimLocalAvailability() {
        val value=Json.decodeFromString<SourceAvailability>("""{"state":"found","sourceType":[],"reason":"local_file_checked"}""")
        assertEquals(SourceHint.Kind.UNKNOWN,hint(value).kind)
    }
    @Test fun localUncertaintyRemainsReadableAndDoesNotDim() {
        val h=hint(local().copy(state="unknown",sourceType=null,reason="local_source_unconfirmed"));assertEquals("Local source needs checking",h.label);assertFalse(h.dimArtwork)
    }
    @Test fun localHintExpiresThroughTheExistingTimer() = runTest {
        val value=local().copy(checkedAt=now.toString(),expiresAt=now.plusSeconds(1).toString())
        val result=async { sourceHintFlow(value,now={now.plusMillis(testScheduler.currentTime)}).take(2).toList() }
        runCurrent();advanceTimeBy(1000);runCurrent();assertEquals(listOf(SourceHint.Kind.LOCAL,SourceHint.Kind.UNKNOWN),result.await().map { it?.kind })
    }
}
