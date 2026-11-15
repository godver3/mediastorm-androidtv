package com.strmr.tv.postplay

import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.ListedTitle
import com.strmr.tv.api.Title
import com.strmr.tv.ratings.PersonalRating
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PostPlayEditorTest {
    private val target = DetailsTarget("tmdb:movie:7","Synthetic film",true)
    private val candidate = Title("tmdb:movie:8","Synthetic choice",mediaType = "movie")
    private class Gateway : PostPlayGateway {
        var current = true
        var rating = "none"
        var title = Title("tmdb:movie:7","Synthetic film",mediaType = "movie")
        var kind = "movie"
        var completion = "movie"
        var source = "personalized"
        var choices = listOf(Title("tmdb:movie:8","Synthetic choice",mediaType = "movie"))
        var negativeChoices = listOf(Title("tmdb:movie:9","Synthetic other choice",mediaType = "movie"))
        var beforeContext: suspend () -> Unit = {}
        var beforeSuggestions: suspend (Int) -> Unit = {}
        var afterWrite: suspend () -> Unit = {}
        var beforeList: suspend () -> Unit = {}
        var writeReply: String? = null
        var suggestionReads = 0
        val writes = mutableListOf<PersonalRating>()
        val listWrites = mutableListOf<String>()
        override fun isCurrent() = current
        override suspend fun context(): PostPlayReply { beforeContext(); return PostPlayReply(title,rating,kind,completion) }
        override suspend fun suggestions(): PostPlaySuggestions {
            val captured = rating
            val items = (if (rating == "dislike") negativeChoices else choices).map { ListedTitle(title = it) }
            val call = ++suggestionReads; beforeSuggestions(call)
            return PostPlaySuggestions(items,source,captured,kind)
        }
        override suspend fun rate(target: DetailsTarget, value: PersonalRating): String { writes += value; rating = value.wire; afterWrite(); return writeReply ?: rating }
        override suspend fun saveToList(target: DetailsTarget) { listWrites += target.titleId; beforeList() }
    }
    @Test fun `opening an unrated completion loads optional question and never writes or navigates`() = runTest {
        val g = Gateway(); var opened = 0; val e = PostPlayEditor(target,g,onRated = {},navigate = { _,_ -> opened++ }); e.load()
        assertTrue(e.state.value.questionVisible); assertEquals(PersonalRating.NONE,e.state.value.rating)
        assertEquals(1,e.state.value.suggestions.size); assertTrue(g.writes.isEmpty()); assertTrue(g.listWrites.isEmpty()); assertEquals(0,opened)
    }
    @Test fun `existing rating is shown without repeating the question and can be edited`() = runTest {
        val g = Gateway(); g.rating = "love"; val e = PostPlayEditor(target,g,onRated = {}); e.load()
        assertEquals(PersonalRating.LOVE,e.state.value.rating); assertFalse(e.state.value.questionVisible)
        e.editRating(); assertTrue(e.state.value.questionVisible); assertTrue(e.state.value.expandedRating); assertTrue(g.writes.isEmpty())
    }
    @Test fun `skip never writes a preference and stays skipped after a retry`() = runTest {
        val g = Gateway(); val e = PostPlayEditor(target,g,onRated = {}); e.load(); e.skipRating(); e.load()
        assertFalse(e.state.value.questionVisible); assertTrue(g.writes.isEmpty()); assertEquals(PersonalRating.NONE,e.state.value.rating)
    }
    @Test fun `confirmed no uses the existing negative rating and refreshes profile suggestions`() = runTest {
        val g = Gateway(); var saved = 0; val e = PostPlayEditor(target,g,onRated = { saved++ }); e.load(); e.rate(PersonalRating.DISLIKE)
        assertEquals(listOf(PersonalRating.DISLIKE),g.writes); assertEquals(PersonalRating.DISLIKE,e.state.value.rating)
        assertEquals("tmdb:movie:9",e.state.value.suggestions.single().id); assertFalse(e.state.value.questionVisible); assertEquals(1,saved)
        assertTrue(g.listWrites.isEmpty())
    }
    @Test fun `love and clearing use the same preference gateway`() = runTest {
        val g = Gateway(); val e = PostPlayEditor(target,g,onRated = {}); e.load(); e.rate(PersonalRating.LOVE); e.rate(PersonalRating.NONE)
        assertEquals(listOf(PersonalRating.LOVE,PersonalRating.NONE),g.writes); assertEquals(PersonalRating.NONE,e.state.value.rating); assertFalse(e.state.value.questionVisible)
    }
    @Test fun `lost acknowledgement keeps confirmed selection and allows idempotent retry`() = runTest {
        val g = Gateway(); var saved = 0; val e = PostPlayEditor(target,g,onRated = { saved++ }); e.load()
        g.afterWrite = { throw IOException("private provider diagnostic") }; e.rate(PersonalRating.LIKE)
        assertEquals(PersonalRating.NONE,e.state.value.rating); assertNotNull(e.state.value.error); assertFalse(e.state.value.error!!.contains("private")); assertEquals(0,saved)
        g.afterWrite = {}; e.rate(PersonalRating.LIKE); assertEquals(PersonalRating.LIKE,e.state.value.rating); assertEquals(1,saved); assertEquals(2,g.writes.size)
    }
    @Test fun `pending save blocks duplicates opposite feedback navigation and list writes`() = runTest {
        val g = Gateway(); val gate = CompletableDeferred<Unit>(); val e = PostPlayEditor(target,g,onRated = {}); e.load(); g.afterWrite = { gate.await() }
        val saving = launch { e.rate(PersonalRating.DISLIKE) }; runCurrent()
        e.rate(PersonalRating.LOVE); assertFalse(e.select(candidate,true)); e.saveToList(candidate)
        assertEquals(listOf(PersonalRating.DISLIKE),g.writes); assertTrue(g.listWrites.isEmpty())
        gate.complete(Unit); saving.join(); assertFalse(e.state.value.saving)
    }
    @Test fun `late old positive suggestions cannot replace a confirmed negative view`() = runTest {
        val g = Gateway(); val gate = CompletableDeferred<Unit>(); g.beforeSuggestions = { if (it == 1) gate.await() }
        val e = PostPlayEditor(target,g,onRated = {}); val initial = launch { e.load() }; runCurrent()
        e.rate(PersonalRating.DISLIKE); assertEquals("tmdb:movie:9",e.state.value.suggestions.single().id)
        gate.complete(Unit); initial.join(); assertEquals(PersonalRating.DISLIKE,e.state.value.rating); assertEquals("tmdb:movie:9",e.state.value.suggestions.single().id)
    }
    @Test fun `unavailable context cannot invent a rating or permit writing`() = runTest {
        val g = Gateway(); g.beforeContext = { throw IOException("private") }; val e = PostPlayEditor(target,g,onRated = {}); e.load(); e.rate(PersonalRating.LIKE)
        assertNull(e.state.value.rating); assertTrue(g.writes.isEmpty()); assertEquals(0,g.suggestionReads)
    }
    @Test fun `wrong completed title kind or completion scope is rejected`() = runTest {
        for (mode in 0..2) {
            val g = Gateway(); when (mode) { 0 -> g.title = g.title.copy(id = "other"); 1 -> g.kind = "series"; else -> g.completion = "episode" }
            val e = PostPlayEditor(target,g,onRated = {}); e.load(); assertNull(e.state.value.rating); assertNotNull(e.state.value.error); assertEquals(0,g.suggestionReads)
        }
    }
    @Test fun `unexpected rating acknowledgement does not confirm a selection`() = runTest {
        val g = Gateway(); val e = PostPlayEditor(target,g,onRated = {}); e.load(); g.writeReply = "five stars"; e.rate(PersonalRating.LIKE)
        assertEquals(PersonalRating.NONE,e.state.value.rating); assertNotNull(e.state.value.error)
    }
    @Test fun `profile changes and closed views reject late reads and writes`() = runTest {
        val reader = Gateway(); reader.beforeContext = { reader.current = false }; val a = PostPlayEditor(target,reader,onRated = {})
        try { a.load(); fail("stale context") } catch (_: CancellationException) {}
        assertNull(a.state.value.title)
        val writer = Gateway(); var saved = 0; val b = PostPlayEditor(target,writer,onRated = { saved++ }); b.load(); writer.afterWrite = { b.close() }
        try { b.rate(PersonalRating.DISLIKE); fail("closed write") } catch (_: CancellationException) {}
        assertEquals(PersonalRating.NONE,b.state.value.rating); assertEquals(0,saved)
    }
    @Test fun `late suggestions cannot update a different profile`() = runTest {
        val g = Gateway(); g.beforeSuggestions = { g.current = false }; val e = PostPlayEditor(target,g,onRated = {})
        try { e.load(); fail("stale suggestions") } catch (_: CancellationException) {}
        assertTrue(e.state.value.suggestions.isEmpty())
    }
    @Test fun `recommendation failure leaves the rating usable and can retry`() = runTest {
        val g = Gateway(); g.beforeSuggestions = { throw IOException("private source") }; val e = PostPlayEditor(target,g,onRated = {}); e.load()
        assertEquals(PersonalRating.NONE,e.state.value.rating); assertNotNull(e.state.value.suggestionsError)
        g.beforeSuggestions = {}; e.refreshSuggestions(); assertEquals(1,e.state.value.suggestions.size); assertNull(e.state.value.suggestionsError)
    }
    @Test fun `suggestions reject contradictory unavailable data and cap each media kind`() = runTest {
        val g = Gateway(); g.source = "unavailable"; val e = PostPlayEditor(target,g,onRated = {}); e.load(); assertNotNull(e.state.value.suggestionsError)
        g.source = "saved_choices"; g.choices = (10..30).map { Title("tmdb:movie:$it","Synthetic",mediaType = "movie") } + (10..30).map { Title("tmdb:tv:$it","Synthetic",mediaType = "series") }
        e.refreshSuggestions(); assertEquals(12,e.state.value.suggestions.size); assertEquals(6,e.state.value.suggestions.count { it.isMovie })
    }
    @Test fun `selection is explicit once and rejects stale or unlisted targets`() = runTest {
        val g = Gateway(); val opened = mutableListOf<Boolean>(); val e = PostPlayEditor(target,g,onRated = {},navigate = { _,play -> opened += play }); e.load()
        assertFalse(e.select(candidate.copy(id = "unknown"),true)); assertTrue(e.select(candidate,false)); assertFalse(e.select(candidate,true)); assertEquals(listOf(false),opened)
        val stale = PostPlayEditor(target,g,onRated = {},navigate = { _,play -> opened += play }); stale.load(); g.current = false
        assertFalse(stale.select(candidate,true)); assertEquals(1,opened.size)
    }
    @Test fun `my list is independent confirmed and deduplicates a pending save`() = runTest {
        val g = Gateway(); val gate = CompletableDeferred<Unit>(); val e = PostPlayEditor(target,g,onRated = {}); e.load(); g.beforeList = { gate.await() }
        val first = launch { e.saveToList(candidate) }; runCurrent(); e.saveToList(candidate); assertEquals(1,g.listWrites.size)
        gate.complete(Unit); first.join(); e.saveToList(candidate); assertEquals(1,g.listWrites.size); assertTrue(g.writes.isEmpty()); assertTrue(e.state.value.savedToList.isNotEmpty())
    }
    @Test fun `failed list save keeps it unconfirmed and allows retry`() = runTest {
        val g = Gateway(); g.beforeList = { throw IOException("private") }; val e = PostPlayEditor(target,g,onRated = {}); e.load(); e.saveToList(candidate)
        assertTrue(e.state.value.savedToList.isEmpty()); assertNotNull(e.state.value.listError)
        g.beforeList = {}; e.saveToList(candidate); assertEquals(2,g.listWrites.size); assertTrue(e.state.value.savedToList.isNotEmpty()); assertTrue(g.writes.isEmpty())
    }
}
