package com.strmr.tv.ratings

import com.strmr.tv.api.DetailsTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TitleRatingEditorTest {
    private val target = DetailsTarget("tmdb:movie:7", "Synthetic title", true)
    private class Gateway : TitleRatingGateway {
        var current = true
        var reads = 0
        val writes = mutableListOf<PersonalRating>()
        var reply = "none"
        var readHook: suspend () -> Unit = {}
        var writeHook: suspend () -> Unit = {}
        var unexpectedWriteReply: String? = null
        override fun isCurrent() = current
        override suspend fun read(target: DetailsTarget): TitleRatingReply { reads++; readHook(); return TitleRatingReply(reply) }
        override suspend fun write(target: DetailsTarget, rating: PersonalRating): TitleRatingReply { writes += rating; writeHook(); reply = rating.wire; return TitleRatingReply(unexpectedWriteReply ?: reply) }
    }
    @Test fun initialLoadDistinguishesUnsetFromUnavailable() = runTest {
        val g = Gateway(); val editor = TitleRatingEditor(target, g)
        assertNull(editor.state.value.rating); assertTrue(editor.state.value.loading)
        editor.load()
        assertEquals(PersonalRating.NONE, editor.state.value.rating); assertFalse(editor.state.value.loading)
        assertEquals(1, g.reads); assertTrue(g.writes.isEmpty())
    }
    @Test fun existingPreferencesLoadWithoutWriting() = runTest {
        for (rating in PersonalRating.entries) {
            val g = Gateway(); g.reply = rating.wire
            val editor = TitleRatingEditor(target, g); editor.load()
            assertEquals(rating, editor.state.value.rating); assertTrue(g.writes.isEmpty())
        }
    }
    @Test fun changingAndRemovingPreferencesRequireConfirmedServerReply() = runTest {
        val g = Gateway(); var saved = 0; val editor = TitleRatingEditor(target, g) { saved++ }; editor.load()
        for (rating in listOf(PersonalRating.LIKE, PersonalRating.LOVE, PersonalRating.DISLIKE, PersonalRating.NONE)) {
            editor.save(rating); assertEquals(rating, editor.state.value.rating); assertNull(editor.state.value.error)
        }
        assertEquals(4, saved); assertEquals(4, g.writes.size)
        editor.save(PersonalRating.NONE); assertEquals(4, g.writes.size)
    }
    @Test fun failedReadCannotInventAnUnsetPreferenceOrPermitWriting() = runTest {
        val g = Gateway(); g.readHook = { throw IOException("private diagnostic") }
        val editor = TitleRatingEditor(target, g); editor.load(); editor.save(PersonalRating.LOVE)
        assertNull(editor.state.value.rating); assertNotNull(editor.state.value.error); assertFalse(editor.state.value.error!!.contains("private")); assertTrue(g.writes.isEmpty())
    }
    @Test fun failedSaveKeepsConfirmedSelectionAndAllowsRetry() = runTest {
        val g = Gateway(); g.reply = "like"; var saved = 0; val editor = TitleRatingEditor(target, g) { saved++ }; editor.load()
        g.writeHook = { throw IOException("secret provider URL") }; editor.save(PersonalRating.LOVE)
        assertEquals(PersonalRating.LIKE, editor.state.value.rating); assertEquals(0, saved); assertNotNull(editor.state.value.error)
        assertFalse(editor.state.value.error!!.contains("secret")); g.writeHook = {}; editor.save(PersonalRating.LOVE)
        assertEquals(PersonalRating.LOVE, editor.state.value.rating); assertEquals(1, saved)
    }
    @Test fun pendingWritePreventsDuplicateAndContradictorySubmissions() = runTest {
        val g = Gateway(); val gate = CompletableDeferred<Unit>(); g.writeHook = { gate.await() }
        val editor = TitleRatingEditor(target, g); editor.load()
        val first = launch { editor.save(PersonalRating.LOVE) }; runCurrent()
        assertTrue(editor.state.value.saving); assertEquals(PersonalRating.NONE, editor.state.value.rating)
        editor.save(PersonalRating.DISLIKE); assertEquals(listOf(PersonalRating.LOVE), g.writes)
        gate.complete(Unit); first.join(); assertEquals(PersonalRating.LOVE, editor.state.value.rating)
    }
    @Test fun profileOrServerChangeBeforeLoadStartsNoRequest() = runTest {
        val g = Gateway(); g.current = false; val editor = TitleRatingEditor(target, g)
        try { editor.load(); fail("Stale context accepted") } catch (_: CancellationException) {}
        assertEquals(0, g.reads)
    }
    @Test fun profileOrServerChangeBeforeSaveStartsNoRequest() = runTest {
        val g = Gateway(); val editor = TitleRatingEditor(target, g); editor.load(); g.current = false
        try { editor.save(PersonalRating.LOVE); fail("Stale context accepted") } catch (_: CancellationException) {}
        assertTrue(g.writes.isEmpty())
    }
    @Test fun lateReadDoesNotDisplayPreviousProfilesPreference() = runTest {
        val g = Gateway(); g.reply = "love"; g.readHook = { g.current = false }; val editor = TitleRatingEditor(target, g)
        try { editor.load(); fail("Late response accepted") } catch (_: CancellationException) {}
        assertNull(editor.state.value.rating)
    }
    @Test fun lateWriteCannotChangeSelectionOrRefreshAnotherProfilesHome() = runTest {
        val g = Gateway(); var saved = 0; val editor = TitleRatingEditor(target, g) { saved++ }; editor.load(); g.writeHook = { g.current = false }
        try { editor.save(PersonalRating.LOVE); fail("Late response accepted") } catch (_: CancellationException) {}
        assertEquals(PersonalRating.NONE, editor.state.value.rating); assertEquals(0, saved)
    }
    @Test fun dismissedEditorIgnoresLateResponse() = runTest {
        val g = Gateway(); var saved = 0; val editor = TitleRatingEditor(target, g) { saved++ }; editor.load(); g.writeHook = { editor.close() }
        try { editor.save(PersonalRating.LOVE); fail("Closed editor accepted reply") } catch (_: CancellationException) {}
        assertEquals(0, saved); assertFalse(editor.isCurrent())
    }
    @Test fun unexpectedServerValueDoesNotConfirmSelection() = runTest {
        val g = Gateway(); var saved = 0; val editor = TitleRatingEditor(target, g) { saved++ }; editor.load(); g.unexpectedWriteReply = "watched"
        editor.save(PersonalRating.LOVE)
        assertEquals(PersonalRating.NONE, editor.state.value.rating); assertNotNull(editor.state.value.error); assertEquals(0, saved)
    }
    @Test fun unknownLoadedValueIsUnavailable() = runTest {
        val g = Gateway(); g.reply = "five stars"; val editor = TitleRatingEditor(target, g); editor.load()
        assertNull(editor.state.value.rating); assertNotNull(editor.state.value.error)
    }
    @Test fun wrongConfirmationDoesNotPretendRequestedPreferenceWasSaved() = runTest {
        val g = Gateway(); g.unexpectedWriteReply = "dislike"; var saved = 0; val editor = TitleRatingEditor(target, g) { saved++ }; editor.load(); editor.save(PersonalRating.LOVE)
        assertEquals(PersonalRating.NONE, editor.state.value.rating); assertNotNull(editor.state.value.error); assertEquals(0, saved)
    }
    @Test fun unmatchedLibraryAndBlankTargetsStartNoRequest() = runTest {
        for (invalid in listOf(target.copy(titleId = ""), target.copy(fromLibrary = true))) {
            val g = Gateway(); val editor = TitleRatingEditor(invalid, g)
            try { editor.load(); fail("Invalid target accepted") } catch (_: IllegalArgumentException) {}
            assertEquals(0, g.reads); assertTrue(g.writes.isEmpty())
        }
    }
    @Test fun lostAcknowledgementCanReapplyPreviouslyDisplayedSelection() = runTest {
        val g = Gateway(); val editor = TitleRatingEditor(target, g); editor.load()
        g.writeHook = { throw IOException("Acknowledgement lost after server commit") }
        editor.save(PersonalRating.LOVE)
        assertEquals(PersonalRating.NONE, editor.state.value.rating)
        g.writeHook = {}; editor.save(PersonalRating.NONE)
        assertEquals(listOf(PersonalRating.LOVE, PersonalRating.NONE), g.writes)
        assertNull(editor.state.value.error)
    }

}
