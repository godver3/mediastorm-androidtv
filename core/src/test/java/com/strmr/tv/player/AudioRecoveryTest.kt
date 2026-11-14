package com.strmr.tv.player

import androidx.media3.common.PlaybackException
import com.strmr.tv.api.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AudioRecoveryTest {
    private fun observation(time: Long, position: Long = time) = AudioAvailability(time, position,
        playing = true, videoSelected = true, expectedAudio = true, hasAudioTracks = false, audioEnabled = true, volume = 1f)
    private val session = PlayerSession(PlaybackRequest(DetailsTarget("tmdb:movie:123", "Synthetic movie", isMovie = true)),
        "https://example.invalid/fixture", tracks = null, streamPath = "/fixture/movie.mkv", prequeueId = "fixture-source")

    @Test fun fatalAudioOutputErrorsAreContextualSuggestions() {
        for (code in listOf(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                            PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED)) {
            assertEquals(AudioRecoveryHint.OUTPUT, AudioRecoveryPolicy().terminalFailure(code, false, true))
        }
    }

    @Test fun decoderErrorsRequireAnAudioRenderer() {
        for (code in listOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                            PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES)) {
            assertNull(AudioRecoveryPolicy().terminalFailure(code, false, true))
            assertEquals(AudioRecoveryHint.DECODER, AudioRecoveryPolicy().terminalFailure(code, true, true))
        }
    }

    @Test fun networkContainerAndUnknownErrorsDoNotBecomeAudioDefects() {
        for (code in listOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            PlaybackException.ERROR_CODE_UNSPECIFIED, 98765)) {
            assertNull(AudioRecoveryPolicy().terminalFailure(code, true, true))
        }
    }

    @Test fun respectsEligibilityBeforeSpendingItsSingleAttempt() {
        val policy = AudioRecoveryPolicy()
        assertNull(policy.terminalFailure(PlaybackException.ERROR_CODE_DECODING_FAILED, true, false))
        assertEquals(AudioRecoveryHint.DECODER, policy.terminalFailure(PlaybackException.ERROR_CODE_DECODING_FAILED, true, true))
        assertNull(policy.terminalFailure(PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, true, true))
        assertNull(policy.observe(observation(0), true))
        assertNull(policy.observe(observation(20_000), true))
    }

    @Test fun onlyVerifiedOnlineMovieOrEpisodePlaybackIsEligible() {
        assertTrue(audioRecoveryEligible(session, true, true))
        assertFalse(audioRecoveryEligible(session, false, true))
        assertFalse(audioRecoveryEligible(session, true, false))
        for (excluded in listOf(session.copy(prequeueId = null), session.copy(prequeueId = ""), session.copy(isLive = true),
                                session.copy(localFile = true), session.copy(reportsProgress = false),
                                session.copy(request = session.request.copy(audioRecoveryCount = 1)))) {
            assertFalse(audioRecoveryEligible(excluded, true, true))
        }
        assertTrue(audioRecoveryEligible(session.copy(request = session.request.copy(episode = PlaybackRequest.EpisodeInfo(1, 2))), true, true))
    }

    @Test fun missingAudioRequiresTenSecondsOfVideoProgressAndExpectedMetadata() {
        val policy = AudioRecoveryPolicy()
        assertNull(policy.observe(observation(0), true))
        assertNull(policy.observe(observation(9_999), true))
        assertEquals(AudioRecoveryHint.MISSING, policy.observe(observation(10_000), true))
        assertNull(policy.observe(observation(30_000), true))
    }

    @Test fun startupStallsMuteAndDisabledAudioCannotTriggerMissingTrackSuggestions() {
        val variants: List<(AudioAvailability) -> AudioAvailability> = listOf(
            { it.copy(playing = false) }, { it.copy(videoSelected = false) }, { it.copy(expectedAudio = false) },
            { it.copy(hasAudioTracks = true) }, { it.copy(audioEnabled = false) }, { it.copy(volume = 0f) },
        )
        for (change in variants) {
            val policy = AudioRecoveryPolicy()
            assertNull(policy.observe(change(observation(0)), true))
            assertNull(policy.observe(change(observation(20_000)), true))
        }
        val stalled = AudioRecoveryPolicy()
        assertNull(stalled.observe(observation(0, 0), true))
        assertNull(stalled.observe(observation(20_000, 0), true))
        val disabled = AudioRecoveryPolicy()
        assertNull(disabled.observe(observation(0), false))
        assertNull(disabled.observe(observation(20_000), false))
    }

    @Test fun pauseAndTrackRecoveryRestartTheMissingAudioWindow() {
        for (interruption in listOf(observation(8_000).copy(playing = false), observation(8_000).copy(hasAudioTracks = true))) {
            val policy = AudioRecoveryPolicy()
            policy.observe(observation(0), true)
            policy.observe(interruption, true)
            assertNull(policy.observe(observation(10_000), true))
            assertNull(policy.observe(observation(19_999), true))
            assertEquals(AudioRecoveryHint.MISSING, policy.observe(observation(20_000), true))
        }
    }

    @Test fun seeksAndBackwardsClockRestartTheObservationWindow() {
        val forward = AudioRecoveryPolicy()
        forward.observe(observation(0), true)
        assertNull(forward.observe(observation(10_000, 90_000), true))
        assertEquals(AudioRecoveryHint.MISSING, forward.observe(observation(20_000, 100_000), true))
        val rewind = AudioRecoveryPolicy()
        rewind.observe(observation(0, 50_000), true)
        assertNull(rewind.observe(observation(10_000, 10_000), true))
        assertEquals(AudioRecoveryHint.MISSING, rewind.observe(observation(20_000, 20_000), true))
        val clock = AudioRecoveryPolicy()
        clock.observe(observation(50_000), true)
        assertNull(clock.observe(observation(1_000), true))
        assertEquals(AudioRecoveryHint.MISSING, clock.observe(observation(11_000), true))
    }

    private class Gateway : ReleaseReportGateway {
        var current = true
        var language = "fra"
        var optionsCalls = 0
        var submits = 0
        var reportingCalls = 0
        var body: JsonObject? = null
        var optionsGate: CompletableDeferred<Unit>? = null
        var submitGate: CompletableDeferred<Unit>? = null
        var phase = "ready"
        var statusDelay = 0L
        val canceled = mutableListOf<String>()
        override fun isCurrent() = current
        override suspend fun options(id: String): ReleaseReportOptions {
            optionsCalls++; optionsGate?.await()
            return ReleaseReportOptions("verified-fixture", audioLanguage = language)
        }
        override suspend fun submit(id: String, reporting: Boolean, body: JsonObject): ReleaseReportReply {
            submits++; if (reporting) reportingCalls++
            this.body = body; submitGate?.await()
            return ReleaseReportReply(replacement = PrequeueStartResponse("fixture-alternative"))
        }
        override suspend fun status(id: String): PrequeueStatus {
            delay(statusDelay)
            return PrequeueStatus(id, phase, streamPath = "/fixture/alternative.mkv", duration = 600.0)
        }
        override suspend fun cancel(id: String) { canceled += id }
        override suspend fun undo(id: String) { fail("Automatic hints must not create or undo a permanent report") }
    }

    @Test fun automaticPreparationNeverReportsOrAdoptsWithoutConfirmation() = runTest {
        for (language in listOf("fra", "spa", "eng", "jpn", "deu")) {
            val gateway = Gateway().apply { this.language = language }
            var adopted: Long? = null
            var position = 42_000L
            val assistant = AudioRecoveryAssistant(this,
                { ReleaseReports("fixture-playing", gateway, this, { position }, { _, offset, _ -> adopted = offset }, clientId = "fixture-device") },
                { gateway.current })
            assertTrue(assistant.suggest(AudioRecoveryHint.DECODER)); advanceUntilIdle()
            assertNotNull(assistant.flow!!.state.value.ready)
            assertEquals(language, assistant.flow!!.state.value.options!!.audioLanguage)
            assertEquals(0, gateway.reportingCalls)
            assertFalse(gateway.body!!.containsKey("reason"))
            assertFalse(gateway.body!!.containsKey("audioLanguage"))
            assertEquals("verified-fixture", gateway.body!!["sourceVersion"]!!.jsonPrimitive.content)
            assertNull(adopted)
            position = 95_000
            assistant.flow!!.take(); assistant.dismiss(); advanceUntilIdle()
            assertEquals(85_000L, adopted)
            assertTrue(gateway.canceled.isEmpty())
        }
    }

    @Test fun openingTheMenuDuringBackgroundLoadDoesNotReloadOrDuplicatePreparation() = runTest {
        val gateway = Gateway().apply { optionsGate = CompletableDeferred() }
        var factories = 0
        val assistant = AudioRecoveryAssistant(this, {
            factories++
            ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> }, clientId = "fixture")
        }, { gateway.current })
        assistant.suggest(AudioRecoveryHint.OUTPUT); runCurrent()
        assistant.flow!!.load()
        assertFalse(assistant.suggest(AudioRecoveryHint.DECODER))
        gateway.optionsGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, factories); assertEquals(1, gateway.optionsCalls); assertEquals(1, gateway.submits)
        assistant.dismiss(); advanceUntilIdle()
        assertEquals(listOf("fixture-alternative"), gateway.canceled)
    }

    @Test fun dismissalCleansUpAReplacementReturnedAfterThePostCompletes() = runTest {
        val gateway = Gateway().apply { submitGate = CompletableDeferred() }
        val assistant = AudioRecoveryAssistant(this,
            { ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> fail("dismissed suggestion adopted") }, clientId = "fixture") }, { gateway.current })
        assistant.suggest(AudioRecoveryHint.OUTPUT); runCurrent()
        assistant.dismiss(); gateway.submitGate!!.complete(Unit); advanceUntilIdle()
        assertFalse(assistant.active)
        assertEquals(listOf("fixture-alternative"), gateway.canceled)
        assertEquals(0, gateway.reportingCalls)
    }

    @Test fun profileChangesAndIneligibleContextsCannotSubmit() = runTest {
        val gateway = Gateway().apply { optionsGate = CompletableDeferred() }
        val assistant = AudioRecoveryAssistant(this,
            { ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> fail("foreign profile adopted") }, clientId = "fixture") }, { gateway.current })
        assistant.suggest(AudioRecoveryHint.DECODER); runCurrent()
        gateway.current = false; gateway.optionsGate!!.complete(Unit); advanceUntilIdle()
        assertFalse(assistant.active); assertEquals(0, gateway.submits)
        assertFalse(assistant.suggest(AudioRecoveryHint.OUTPUT))
    }

    @Test fun failedAndTimedOutPreparationKeepTheSuggestionManualAndBounded() = runTest {
        for (timeout in listOf(false, true)) {
            val gateway = Gateway().apply { phase = "failed"; statusDelay = if (timeout) 100_000 else 0 }
            val assistant = AudioRecoveryAssistant(this,
                { ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> fail("failed source adopted") }, clientId = "fixture") }, { gateway.current })
            assistant.suggest(AudioRecoveryHint.DECODER); advanceUntilIdle()
            val state = assistant.flow!!.state.value
            assertFalse(state.busy); assertNull(state.ready)
            assertTrue(audioRecoveryMessage(AudioRecoveryHint.DECODER, state).contains("could not finish"))
            assertEquals(1, gateway.submits); assertEquals(0, gateway.reportingCalls)
            assertEquals(listOf("fixture-alternative"), gateway.canceled)
            assistant.dismiss()
        }
    }

    @Test fun progressMessagesDescribeSuggestionsWithoutClaimingAFileDefect() {
        val preparing = audioRecoveryMessage(AudioRecoveryHint.OUTPUT, ReleaseReportState(busy = true))
        assertTrue(preparing.lowercase().contains("this device")); assertTrue(preparing.contains("No report"))
        val ready = audioRecoveryMessage(AudioRecoveryHint.MISSING, ReleaseReportState(ready = PrequeueStatus("p", "ready")))
        assertTrue(ready.contains("Review it before switching"))
        assertFalse(ready.contains("file is defective"))
    }

    @Test fun onlyDeliberateKnownDetailsAudioChoicesBecomeSavedIntent() {
        val tracks = listOf(ProbedAudioTrack(2, "fra"), ProbedAudioTrack(7, "eng"), ProbedAudioTrack(8, "und"))
        assertEquals("eng", ManualSourceTracks(ManualTrackChoice(audio = 7), tracks, emptyList()).explicitAudioLanguage)
        assertNull(ManualSourceTracks(ManualTrackChoice(subtitle = -1), tracks, emptyList()).explicitAudioLanguage)
        assertNull(ManualSourceTracks(ManualTrackChoice(audio = 8), tracks, emptyList()).explicitAudioLanguage)
        assertNull(ManualSourceTracks(ManualTrackChoice(audio = 99), tracks, emptyList()).explicitAudioLanguage)
    }

    @Test fun automaticPreparationWaitsForEnglishChosenOnDetails() = runTest {
        for (profileLanguage in listOf("fra", "spa")) {
            val gateway = Gateway().apply { language = profileLanguage }
            val saved = CompletableDeferred<Unit>()
            val picked = ManualSourceTracks(ManualTrackChoice(audio = 7), listOf(ProbedAudioTrack(7, "eng")), emptyList())
            val assistant = AudioRecoveryAssistant(this, {
                ReleaseReports("playing", gateway, this, { 0 }, { _, _, _ -> }, beforeOptions = {
                    saved.await()
                    gateway.language = picked.explicitAudioLanguage!!
                }, clientId = "fixture")
            }, { gateway.current })
            assistant.suggest(AudioRecoveryHint.DECODER); runCurrent()
            assertEquals(0, gateway.optionsCalls); assertEquals(0, gateway.submits)
            saved.complete(Unit); advanceUntilIdle()
            assertEquals("eng", assistant.flow!!.state.value.options!!.audioLanguage)
            assertEquals(1, gateway.submits); assertEquals(0, gateway.reportingCalls)
            assistant.dismiss(); advanceUntilIdle()
        }
    }

    @Test fun automaticAudioBudgetRestartsForTheNextEpisodeOnly() {
        val current = session.request.copy(episode = PlaybackRequest.EpisodeInfo(1, 1),
            following = listOf(PlaybackRequest.EpisodeInfo(1, 2)), audioRecoveryCount = 1)
        assertFalse(audioRecoveryEligible(session.copy(request = current), true, true))
        val sameEpisodeAfterSeek = SourceSwitchRules.resumeAt(current, 42_000)
        assertFalse(audioRecoveryEligible(session.copy(request = sameEpisodeAfterSeek), true, true))
        val next = current.nextEpisode!!
        assertEquals(2, next.episode!!.episode)
        assertEquals(0, next.audioRecoveryCount)
        assertTrue(audioRecoveryEligible(session.copy(request = next, prequeueId = "next-episode-fixture"), true, true))
    }
}
