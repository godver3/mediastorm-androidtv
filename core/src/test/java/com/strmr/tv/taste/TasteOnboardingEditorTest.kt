package com.strmr.tv.taste

import com.strmr.tv.api.Title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TasteOnboardingEditorTest {
    private val movie = Title("tmdb:movie:7", "Synthetic private name", mediaType = "movie", tmdbId = 7)
    private val series = Title("tmdb:tv:7", "Synthetic Series", mediaType = "series", tmdbId = 7)
    private class Gateway : TasteSetupGateway {
        var current = true
        var reply = TasteSetupReply("new", available = true)
        var readCalls = 0
        var writes = mutableListOf<Pair<String, List<Title>>>()
        var candidates = emptyList<Title>()
        var readHook: suspend () -> Unit = {}
        var writeHook: suspend () -> Unit = {}
        var find: (suspend (String, Boolean) -> List<Title>)? = null
        override fun isCurrent() = current
        override suspend fun read(): TasteSetupReply { readCalls++; readHook(); return reply }
        override suspend fun candidates(query: String, movie: Boolean): List<Title> = find?.invoke(query, movie) ?: candidates
        override suspend fun save(action: String, titles: List<Title>): TasteSetupReply {
            writes += action to titles; writeHook()
            return if (action == "skip") reply.copy(status = "skipped") else reply.copy(status = "completed", source = if (action == "history") "history" else "manual", titles = titles)
        }
    }
    @Test fun loadDoesNotCreatePreferencesOrHistory() = runTest {
        val g = Gateway(); val editor = TasteOnboardingEditor(g, intro = true); editor.load()
        assertTrue(editor.state.value.intro); assertTrue(editor.state.value.available); assertTrue(editor.state.value.selected.isEmpty()); assertTrue(g.writes.isEmpty())
    }
    @Test fun choicesAreStagedUntilExplicitSave() = runTest {
        val g = Gateway(); var saved = 0; val editor = TasteOnboardingEditor(g, onSaved = { saved++ }); editor.load(); editor.toggle(movie); editor.toggle(series)
        assertEquals(2, editor.state.value.selected.size); assertTrue(g.writes.isEmpty())
        editor.finish(); assertTrue(editor.state.value.finished); assertEquals(1, saved); assertEquals("save", g.writes.single().first)
    }
    @Test fun closingStagedChangesNeverWrites() = runTest {
        val g = Gateway(); val editor = TasteOnboardingEditor(g); editor.load(); editor.toggle(movie); editor.close()
        assertTrue(g.writes.isEmpty()); assertFalse(editor.isCurrent())
    }
    @Test fun skipDoesNotSendTheStagedSelection() = runTest {
        val g = Gateway(); val editor = TasteOnboardingEditor(g); editor.load(); editor.toggle(movie); editor.finish("skip")
        assertEquals("skip", g.writes.single().first); assertTrue(g.writes.single().second.isEmpty()); assertTrue(editor.state.value.finished)
    }
    @Test fun clearingExistingInterestsSavesAnEmptySelection() = runTest {
        val g = Gateway(); g.reply = TasteSetupReply("completed", "manual", listOf(movie), available = true)
        val editor = TasteOnboardingEditor(g); editor.load(); editor.clearSelection(); assertTrue(g.writes.isEmpty()); editor.finish()
        assertEquals("save", g.writes.single().first); assertTrue(g.writes.single().second.isEmpty())
    }
    @Test fun historyRequiresAnExplicitAvailableChoice() = runTest {
        val g = Gateway(); val editor = TasteOnboardingEditor(g); editor.load(); editor.finish("history"); assertTrue(g.writes.isEmpty())
        g.reply = g.reply.copy(canUseHistory = true); editor.load(); editor.finish("history")
        assertEquals("history", g.writes.single().first); assertTrue(g.writes.single().second.isEmpty())
    }
    @Test fun requestContainsOnlyActionAndTitleCoordinates() {
        val body = tasteSetupBody("save", listOf(movie, series)).toString()
        assertFalse(body.contains(movie.name)); assertTrue(body.contains("\"titleId\":\"tmdb:movie:7\"")); assertTrue(body.contains("\"mediaType\":\"series\""))
        assertEquals("{\"action\":\"skip\"}", tasteSetupBody("skip", emptyList()).toString())
        assertEquals("{\"action\":\"history\"}", tasteSetupBody("history", emptyList()).toString())
    }
    @Test fun verifiedAliasesToggleOneInterestWithoutCrossingKind() = runTest {
        val g = Gateway(); val editor = TasteOnboardingEditor(g); editor.load(); editor.toggle(movie)
        val alias = movie.copy(id = "tt7", imdbId = "tt7"); assertTrue(editor.selected(alias)); editor.toggle(alias); assertTrue(editor.state.value.selected.isEmpty())
        editor.toggle(movie); editor.toggle(series); assertEquals(2, editor.state.value.selected.size)
    }
    @Test fun maximumSelectionStillAllowsRemoval() = runTest {
        val g = Gateway(); val editor = TasteOnboardingEditor(g); editor.load()
        repeat(40) { editor.toggle(movie.copy(id = "tmdb:movie:${it + 1}", tmdbId = (it + 1).toLong())) }
        editor.toggle(movie.copy(id = "tmdb:movie:99", tmdbId = 99)); assertEquals(40, editor.state.value.selected.size); assertNotNull(editor.state.value.error)
        editor.toggle(movie); assertEquals(39, editor.state.value.selected.size)
    }
    @Test fun unavailableProfileCannotStageOrSave() = runTest {
        val g = Gateway(); g.reply = TasteSetupReply("new", available = false, mediaTypes = emptyList()); val editor = TasteOnboardingEditor(g); editor.load(); editor.toggle(movie); editor.finish()
        assertTrue(editor.state.value.selected.isEmpty()); assertTrue(g.writes.isEmpty())
    }
    @Test fun failedLoadDoesNotInventAnEmptySuccessfulSetup() = runTest {
        val g = Gateway(); g.readHook = { throw IOException("private provider URL") }; val editor = TasteOnboardingEditor(g); editor.load(); editor.finish()
        assertNotNull(editor.state.value.error); assertFalse(editor.state.value.available); assertFalse(editor.state.value.finished); assertTrue(g.writes.isEmpty()); assertFalse(editor.state.value.error!!.contains("private"))
    }
    @Test fun failedSaveKeepsStagedChoicesAndCanRetry() = runTest {
        val g = Gateway(); var saved = 0; val editor = TasteOnboardingEditor(g, onSaved = { saved++ }); editor.load(); editor.toggle(movie); g.writeHook = { throw IOException("lost acknowledgement") }; editor.finish()
        assertEquals(listOf(movie), editor.state.value.selected); assertFalse(editor.state.value.finished); assertEquals(0, saved)
        g.writeHook = {}; editor.finish(); assertTrue(editor.state.value.finished); assertEquals(1, saved); assertEquals(2, g.writes.size)
    }
    @Test fun pendingSaveBlocksDoubleSubmissionAndSelectionChanges() = runTest {
        val g = Gateway(); val gate = CompletableDeferred<Unit>(); g.writeHook = { gate.await() }; val editor = TasteOnboardingEditor(g); editor.load(); editor.toggle(movie)
        val request = launch { editor.finish() }; runCurrent(); editor.toggle(series); editor.finish("skip")
        assertEquals(listOf(movie), editor.state.value.selected); assertEquals(1, g.writes.size); gate.complete(Unit); request.join()
    }
    @Test fun lateSaveAfterProfileSwitchCannotFinishOrRefreshAnotherProfile() = runTest {
        val g = Gateway(); var saved = 0; val editor = TasteOnboardingEditor(g, onSaved = { saved++ }); editor.load(); editor.toggle(movie); g.writeHook = { g.current = false }
        try { editor.finish(); fail("Stale reply accepted") } catch (_: CancellationException) {}
        assertFalse(editor.state.value.finished); assertEquals(0, saved)
    }
    @Test fun staleContextBeforeLoadStartsNoRequest() = runTest {
        val g = Gateway(); g.current = false; val editor = TasteOnboardingEditor(g)
        try { editor.load(); fail("Stale request accepted") } catch (_: CancellationException) {}
        assertEquals(0, g.readCalls)
    }
    @Test fun lateReadNeverShowsAnotherProfilesStartingInterests() = runTest {
        val g = Gateway(); g.reply = g.reply.copy(titles = listOf(movie)); g.readHook = { g.current = false }; val editor = TasteOnboardingEditor(g)
        try { editor.load(); fail("Stale reply accepted") } catch (_: CancellationException) {}
        assertTrue(editor.state.value.selected.isEmpty())
    }
    @Test fun outOfOrderSearchCannotReplaceTheLatestCandidatesOrStagedChoices() = runTest {
        val g = Gateway(); val first = CompletableDeferred<Unit>(); g.find = { query, _ -> if (query == "first") { first.await(); listOf(movie) } else listOf(movie.copy(id = "tmdb:movie:8", tmdbId = 8)) }
        val editor = TasteOnboardingEditor(g); editor.load(); editor.toggle(series)
        val old = launch { editor.search("first", true) }; runCurrent(); editor.search("latest", true); first.complete(Unit); old.join()
        assertEquals("tmdb:movie:8", editor.state.value.candidates.single().id); assertEquals(listOf(series), editor.state.value.selected)
    }
    @Test fun unknownPeopleAndWrongMediaKindAreNotCandidates() = runTest {
        val g = Gateway(); g.candidates = listOf(movie, series, movie.copy(id = "person:7", mediaType = "person")); val editor = TasteOnboardingEditor(g); editor.load(); editor.search("", true)
        assertEquals(listOf(movie), editor.state.value.candidates)
    }
    @Test fun restrictedMediaKindStartsNoCandidateRequest() = runTest {
        val g = Gateway(); var calls = 0; g.find = { _, _ -> calls++; listOf(movie) }; g.reply = g.reply.copy(mediaTypes = listOf("series")); val editor = TasteOnboardingEditor(g); editor.load(); editor.search("", true)
        assertEquals(0, calls); editor.search("", false); assertEquals(1, calls)
    }
    @Test fun unexpectedStatusCannotConfirmSetup() = runTest {
        val g = Gateway(); g.reply = TasteSetupReply("invented", available = true); val editor = TasteOnboardingEditor(g); editor.load()
        assertNotNull(editor.state.value.error); assertFalse(editor.state.value.available)
    }
    @Test fun initialOfferIsUsedOnceAndRetryReadsTheCurrentServerState() = runTest {
        val g = Gateway(); val initial = TasteSetupReply("new", available = true); val editor = TasteOnboardingEditor(g, initial = initial, intro = true); editor.load(); assertEquals(0, g.readCalls)
        g.reply = TasteSetupReply("completed", "manual", listOf(movie), available = true); editor.load(); assertEquals(1, g.readCalls); assertEquals(listOf(movie), editor.state.value.selected)
    }
    @Test fun failedCandidateSearchRetainsChoicesAndRetriesWithoutSaving() = runTest {
        val g = Gateway(); g.find = { _, _ -> throw IOException("private provider failure") }; val editor = TasteOnboardingEditor(g); editor.load(); editor.toggle(movie); editor.search("first", true)
        assertEquals(listOf(movie), editor.state.value.selected); assertNotNull(editor.state.value.searchError); assertFalse(editor.state.value.searchError!!.contains("private")); assertTrue(g.writes.isEmpty())
        g.find = { _, _ -> listOf(movie) }; editor.search("first", true); assertNull(editor.state.value.searchError); assertEquals(listOf(movie), editor.state.value.candidates)
    }

}
