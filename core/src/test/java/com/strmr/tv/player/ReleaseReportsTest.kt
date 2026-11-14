package com.strmr.tv.player

import com.strmr.tv.api.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReleaseReportsTest {
    private class Gateway : ReleaseReportGateway {
        var current = true
        var language = "fra"
        var episode = false
        var calls = 0
        var lastBody: JsonObject? = null
        var reported = false
        var gate: CompletableDeferred<Unit>? = null
        var optionsFailure = false
        var pendingStatus = "ready"
        var statusDelay = 0L
        val canceled = mutableListOf<String>()
        val removed = mutableListOf<String>()
        override fun isCurrent() = current
        override suspend fun options(id: String): ReleaseReportOptions {
            if (optionsFailure) error("fixture unavailable")
            return ReleaseReportOptions("verified-version", "Synthetic.Movie.1080p", language,
                listOf(ProbedAudioTrack(2, "fra", "aac"), ProbedAudioTrack(3, "eng", "aac")), 2, hasEpisode = episode)
        }
        override suspend fun submit(id: String, reporting: Boolean, body: JsonObject): ReleaseReportReply {
            calls++; lastBody = body; reported = reporting
            gate?.await()
            return ReleaseReportReply(if (reporting) SavedReleaseReport("fixture-report") else null,
                if (body["retry"]?.jsonPrimitive?.boolean == true) PrequeueStartResponse("replacement") else null)
        }
        override suspend fun status(id: String): PrequeueStatus {
            delay(statusDelay)
            return PrequeueStatus(id, pendingStatus, streamPath = "/fixture/alternative.mkv", duration = 600.0)
        }
        override suspend fun cancel(id: String) { canceled += id }
        override suspend fun undo(id: String) { removed += id }
    }

    @Test fun preparesBeforeExplicitAdoptionAndUsesLatestPosition() = runTest {
        val gateway = Gateway()
        var position = 42_000L
        var adopted: Long? = null
        val flow = ReleaseReports("playing", gateway, this, { position }, { _, offset, _ -> adopted = offset }, clientId = "fixture-device")
        flow.load(); flow.choose(ReleaseProblem.LANGUAGE); flow.submit(true, true); advanceUntilIdle()
        assertNotNull(flow.state.value.ready)
        assertNull("Preparation must not stop the playing source", adopted)
        assertEquals("fra", gateway.lastBody!!["audioLanguage"]!!.jsonPrimitive.content)
        assertEquals("verified-version", gateway.lastBody!!["sourceVersion"]!!.jsonPrimitive.content)
        assertFalse(gateway.lastBody!!.containsKey("releaseName"))
        assertFalse(gateway.lastBody!!.containsKey("streamPath"))
        position = 95_000
        flow.take(); flow.close(); advanceUntilIdle()
        assertEquals(85_000L, adopted)
        assertTrue("Transferred replacement must not be deleted on menu disposal", gateway.canceled.isEmpty())
    }

    @Test fun languageIntentIsNotFixedToGerman() = runTest {
        for (language in listOf("fra", "spa", "eng", "jpn", "deu")) {
            val gateway = Gateway().apply { this.language = language }
            val flow = ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> fail("report-only adopted") }, clientId = "fixture")
            flow.load(); flow.choose(ReleaseProblem.LANGUAGE); flow.submit(true, false); advanceUntilIdle()
            assertEquals(language, gateway.lastBody!!["audioLanguage"]!!.jsonPrimitive.content)
            assertEquals("fixture-report", flow.state.value.reportId)
            assertNull(flow.state.value.ready)
            flow.undo(); advanceUntilIdle()
            assertEquals(listOf("fixture-report"), gateway.removed)
            flow.close()
        }
    }

    @Test fun wrongContentAlwaysStartsAtZero() = runTest {
        for (reason in listOf(ReleaseProblem.WRONG_TITLE, ReleaseProblem.WRONG_EPISODE)) {
            val gateway = Gateway().apply { episode = true }
            var result: Long? = null
            var wrong = false
            val flow = ReleaseReports("playing", gateway, this, { 240_000 }, { _, offset, reset -> result = offset; wrong = reset }, clientId = "fixture")
            flow.load(); flow.choose(reason); flow.resume(ReleaseResume.CURRENT); flow.submit(true, true); advanceUntilIdle()
            flow.take()
            assertEquals(0L, result); assertTrue(wrong)
        }
    }

    @Test fun resumeModesClampAndDoNotClaimEditAlignment() {
        assertEquals(0L, ReleaseResume.REWIND.position(5_000, false))
        assertEquals(0L, ReleaseResume.REWIND.position(-5, false))
        assertEquals(0L, ReleaseResume.BEGINNING.position(70_000, false))
        assertEquals(70_000L, ReleaseResume.CURRENT.position(70_000, false))
        assertEquals(60_000L, ReleaseResume.REWIND.position(70_000, false))
        assertEquals(0L, ReleaseResume.CURRENT.position(700_000, false, 600.0))
        assertEquals(0L, ReleaseResume.REWIND.position(70_000, true))
    }

    @Test fun duplicatePreparationIsIgnoredAndManualRetryIsTransient() = runTest {
        val gateway = Gateway()
        val flow = ReleaseReports("playing", gateway, this, { 42_000 }, { _, _, _ -> }, clientId = "fixture")
        flow.load(); flow.submit(false, true); flow.submit(false, true); advanceUntilIdle()
        assertEquals(1, gateway.calls)
        assertFalse(gateway.reported)
        assertFalse(gateway.lastBody!!.containsKey("reason"))
        assertFalse(gateway.lastBody!!.containsKey("audioLanguage"))
        assertNull(flow.state.value.reportId)
        flow.close(); advanceUntilIdle()
        assertEquals(listOf("replacement"), gateway.canceled)
    }

    @Test fun closingWhileSubmissionIsInFlightCleansUpLateReplacement() = runTest {
        val gateway = Gateway().apply { gate = CompletableDeferred() }
        val flow = ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> fail("closed flow adopted") }, clientId = "fixture")
        flow.load(); flow.submit(false, true); runCurrent()
        flow.close(); gateway.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("replacement"), gateway.canceled)
        assertNull(flow.state.value.ready)
    }

    @Test fun profileChangeBeforeAdoptionCannotSwitchPlayback() = runTest {
        val gateway = Gateway()
        val flow = ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> fail("different profile adopted") }, clientId = "fixture")
        flow.load(); flow.submit(false, true); advanceUntilIdle()
        gateway.current = false; flow.take(); advanceUntilIdle()
        assertTrue(flow.state.value.message!!.contains("context changed"))
        flow.close()
    }

    @Test fun failedAndTimedOutAlternativesKeepCurrentPlayback() = runTest {
        for (timeout in listOf(false, true)) {
            val gateway = Gateway().apply { pendingStatus = "failed"; statusDelay = if (timeout) 100_000 else 0 }
            val flow = ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> fail("failed source adopted") }, clientId = "fixture")
            flow.load(); flow.submit(false, true); advanceUntilIdle()
            assertFalse(flow.state.value.busy)
            assertNull(flow.state.value.ready)
            assertEquals(listOf("replacement"), gateway.canceled)
            assertTrue(flow.state.value.message!!.contains(if (timeout) "timed out" else "No suitable"))
            flow.close()
        }
    }

    @Test fun trackReportRequiresExplicitAffectedTrack() = runTest {
        val gateway = Gateway()
        val flow = ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> }, clientId = "fixture")
        flow.load(); flow.choose(ReleaseProblem.AUDIO); flow.submit(true, false); advanceUntilIdle()
        assertEquals(0, gateway.calls)
        flow.track(99); flow.submit(true, false); advanceUntilIdle()
        assertEquals(0, gateway.calls)
        flow.track(3); flow.submit(true, false); advanceUntilIdle()
        assertEquals(3, gateway.lastBody!!["audioTrackIndex"]!!.jsonPrimitive.int)
        flow.close()
    }

    @Test fun staleOptionsAreNotUsedAfterContextChange() = runTest {
        val gateway = Gateway()
        val flow = ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> }, clientId = "fixture")
        flow.load(); flow.choose(ReleaseProblem.LANGUAGE); gateway.current = false
        flow.submit(true, false); advanceUntilIdle()
        assertEquals(0, gateway.calls)
        flow.close()
    }

    @Test fun oldServerDoesNotOfferUnboundReports() = runTest {
        val gateway = Gateway().apply { optionsFailure = true }
        val flow = ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> }, clientId = "fixture")
        flow.load(); flow.choose(ReleaseProblem.LANGUAGE); flow.submit(true, false); advanceUntilIdle()
        assertNull(flow.state.value.options); assertFalse(flow.state.value.busy); assertEquals(0, gateway.calls)
        flow.close()
    }

    @Test fun deliberateEnglishChoicePreservesSubtitleOverrides() {
        val existing = buildJsonObject { put("audioLanguage", "fra"); put("subtitleLanguage", "spa"); put("subtitleMode", "foreign-audio") }
        val updated = contentAudioPreference(existing, "tmdb:movie:123", "movie", "eng")
        assertEquals("eng", updated["audioLanguage"]!!.jsonPrimitive.content)
        assertEquals("spa", updated["subtitleLanguage"]!!.jsonPrimitive.content)
        assertEquals("foreign-audio", updated["subtitleMode"]!!.jsonPrimitive.content)
        assertEquals("fra", existing["audioLanguage"]!!.jsonPrimitive.content)
    }

    @Test fun wireModelsUseServerTrackIndicesAndIgnoreUnknownFields() {
        val options = ApiJson.decodeFromString<ReleaseReportOptions>("""{"sourceVersion":"fixture","audioLanguage":"spa","audioTracks":[{"index":7,"language":"spa","codec":"aac"}],"selectedAudioTrack":7,"futureField":true}""")
        assertEquals("spa", options.audioLanguage); assertEquals(7, options.audioTracks.single().index)
        val reply = ApiJson.decodeFromString<ReleaseReportReply>("""{"report":{"id":"r","scope":{"kind":"language"}},"replacement":{"prequeueId":"p","status":"queued"}}""")
        assertEquals("r", reply.report!!.id); assertEquals("p", reply.replacement!!.prequeueId)
    }

    @Test fun menusRespectEpisodeAndAffectedTrackRequirements() = runTest {
        val flow = ReleaseReports("playing", Gateway(), this, { 0 }, { _, _, _ -> }, clientId = "fixture")
        flow.load()
        assertFalse(releaseReportChoices(flow, flow.state.value) {}.any { it.title == ReleaseProblem.WRONG_EPISODE.label })
        flow.choose(ReleaseProblem.AUDIO)
        assertFalse(releaseReportChoices(flow, flow.state.value) {}.any { it.title == "Report only" })
        flow.track(3)
        assertTrue(releaseReportChoices(flow, flow.state.value) {}.any { it.title == "Report only" })
        flow.close()
    }

    @Test fun preparedMenuOffersResumeChoicesOnlyForMatchingContent() = runTest {
        for (reason in listOf(ReleaseProblem.LANGUAGE, ReleaseProblem.WRONG_TITLE)) {
            val flow = ReleaseReports("playing", Gateway(), this, { 0 }, { _, _, _ -> }, clientId = "fixture")
            flow.load(); flow.choose(reason); flow.submit(true, true); advanceUntilIdle()
            val choices = releaseReportChoices(flow, flow.state.value) {}
            for (mode in ReleaseResume.entries) assertEquals(!reason.wrongContent, choices.any { it.title == mode.label })
            assertTrue(choices.any { it.title == "Start prepared alternative" })
            flow.close(); advanceUntilIdle()
        }
    }

    @Test fun remoteCanFocusCancelWhilePreparationIsPending() = runTest {
        val gateway = Gateway().apply { gate = CompletableDeferred() }
        val flow = ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> }, clientId = "fixture")
        flow.load(); flow.submit(false, true); runCurrent()
        val choices = releaseReportChoices(flow, flow.state.value) { flow.close() }
        assertEquals("Cancel preparation", choices.first().title)
        choices.first().onClick()
        gateway.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("replacement"), gateway.canceled)
    }

    @Test fun dialogWaitsForDeliberateLanguageSave() = runTest {
        val gateway = Gateway()
        val saved = CompletableDeferred<Unit>()
        val flow = ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> },
            beforeOptions = { saved.await() }, clientId = "fixture")
        val loading = launch { flow.load() }
        runCurrent(); assertNull(flow.state.value.options)
        gateway.language = "eng"; saved.complete(Unit); loading.join()
        assertEquals("eng", flow.state.value.options!!.audioLanguage)
        flow.close()
    }
}
