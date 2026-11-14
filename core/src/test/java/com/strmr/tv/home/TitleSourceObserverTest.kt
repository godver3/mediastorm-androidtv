package com.strmr.tv.home

import com.strmr.tv.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TitleSourceObserverTest {
    private fun title(n: Int) = TitleSourceCoordinate("tmdb:movie:$n", "movie")
    private fun source(reason: String = "matching_candidates") = SourceAvailability(state = "found", reason = reason)
    private inner class Gateway : TitleSourceGateway {
        var current = true
        var enabled = true
        val calls = mutableListOf<Pair<String, TitleSourceDemand>>()
        var intercept: (suspend (TitleSourceDemand) -> TitleSourceResponse?)? = null
        override fun isCurrent() = current
        override suspend fun update(view: String, body: TitleSourceDemand): TitleSourceResponse {
            calls += view to body
            intercept?.invoke(body)?.let { return it }
            return TitleSourceResponse(enabled, body.version, body.titles.map { TitleSourceItem(it.titleId, it.mediaType, source()) })
        }
    }
    @Test fun visibleTitlesAreBatchedAndExplicitCleanupIsOrdered() = runTest {
        val g = Gateway(); val values = TitleSourceObserver(g).states(listOf(title(1), title(2))).take(2).toList()
        assertTrue(values.first().isEmpty()); assertEquals(2, values.last().size)
        assertEquals(2, g.calls.first().second.titles.size); assertTrue(g.calls.last().second.titles.isEmpty())
        assertTrue(g.calls.last().second.version > g.calls.first().second.version)
        assertTrue(TitleSourceDemand(7).toTitleSourceBody().toString().contains("\"titles\":[]"))
        assertFalse(g.calls.first().second.toTitleSourceBody().toString().contains("query"))
    }
    @Test fun invalidDuplicatesAndExcessCardsAreExcluded() = runTest {
        val g = Gateway(); TitleSourceObserver(g).states((1..40).map(::title) + title(1) + TitleSourceCoordinate("person", "person")).take(2).toList()
        assertEquals(24, g.calls.first().second.titles.size); assertEquals(24, g.calls.first().second.titles.distinct().size)
    }
    @Test fun emptyViewStartsNoRequest() = runTest {
        val g = Gateway(); TitleSourceObserver(g).states(emptyList()).toList(); assertTrue(g.calls.isEmpty())
    }
    @Test fun responseMustMatchVersionAndMediaType() = runTest {
        for (wrongVersion in listOf(true, false)) {
            val g = Gateway(); g.intercept = { b -> if (b.titles.isEmpty()) null else TitleSourceResponse(true, if (wrongVersion) b.version - 1 else b.version, listOf(TitleSourceItem(title(1).titleId, "series", source()))) }
            val values = TitleSourceObserver(g).states(listOf(title(1))).take(2).toList()
            assertTrue(values.last().isEmpty())
        }
    }
    @Test fun duplicateResponseRowsStayUnknown() = runTest {
        val g = Gateway(); g.intercept = { b -> if (b.titles.isEmpty()) null else TitleSourceResponse(true, b.version, List(2) { TitleSourceItem(title(1).titleId, "movie", source()) }) }
        assertTrue(TitleSourceObserver(g).states(listOf(title(1))).take(2).toList().last().isEmpty())
    }
    @Test fun disabledServerDoesNotRenew() = runTest {
        val g = Gateway(); g.enabled = false; TitleSourceObserver(g).states(listOf(title(1))).toList(); assertEquals(1, g.calls.size)
    }
    @Test fun completedEvidenceRenewsAtFifteenSeconds() = runTest {
        val g = Gateway(); val job = launch { TitleSourceObserver(g).states(listOf(title(1))).collect {} }
        runCurrent(); advanceTimeBy(14_999); runCurrent(); assertEquals(1, g.calls.size)
        advanceTimeBy(1); runCurrent(); assertEquals(2, g.calls.size); job.cancelAndJoin()
    }
    @Test fun pendingEvidenceHasBoundedFastPolling() = runTest {
        val g = Gateway(); g.intercept = { b -> if (b.titles.isEmpty()) null else TitleSourceResponse(true, b.version, b.titles.map { TitleSourceItem(it.titleId, it.mediaType, source("not_checked")) }) }
        val job = launch { TitleSourceObserver(g).states(listOf(title(1))).collect {} }; runCurrent()
        advanceTimeBy(25_000); runCurrent(); assertEquals(16, g.calls.size)
        advanceTimeBy(5_000); runCurrent(); assertEquals(17, g.calls.size); job.cancelAndJoin()
    }
    @Test fun identityChangeDropsResponseAndNeverCleansUnderNewIdentity() = runTest {
        val g = Gateway(); g.intercept = { g.current = false; TitleSourceResponse(true, it.version, listOf(TitleSourceItem(title(1).titleId, "movie", source()))) }
        val values = TitleSourceObserver(g).states(listOf(title(1))).toList(); assertEquals(1, values.size); assertEquals(1, g.calls.size)
    }
    @Test fun failureClearsHintsAndReleasesItsView() = runTest {
        val g = Gateway(); g.intercept = { if (it.titles.isNotEmpty()) throw IOException("synthetic outage") else null }
        assertTrue(TitleSourceObserver(g).states(listOf(title(1))).toList().all { it.isEmpty() })
        assertTrue(g.calls.last().second.titles.isEmpty())
    }
    @Test fun lateOldResponseDoesNotOverrideTheNewBatch() = runTest {
        val g = Gateway(); val deferred = CompletableDeferred<TitleSourceResponse>()
        g.intercept = { b -> if (b.titles == listOf(title(1))) withContext(NonCancellable) { deferred.await() } else null }
        val observer = TitleSourceObserver(g); val old = mutableListOf<Map<String, SourceAvailability>>()
        val first = launch { observer.states(listOf(title(1))).collect { old += it } }; runCurrent(); first.cancel()
        val second = launch { observer.states(listOf(title(2))).collect {} }; runCurrent()
        val version = g.calls.last().second.version
        deferred.complete(TitleSourceResponse(true, g.calls.first().second.version, listOf(TitleSourceItem(title(1).titleId, "movie", source())))); runCurrent()
        assertTrue(old.all { it.isEmpty() }); assertEquals(version, g.calls.last().second.version)
        second.cancelAndJoin(); first.join()
    }
    @Test fun scrollBatchesReuseOneViewAndIncreasingVersions() = runTest {
        val g = Gateway(); val observer = TitleSourceObserver(g)
        for (n in 1..30) observer.states(listOf(title(n))).take(2).toList()
        assertEquals(1, g.calls.map { it.first }.distinct().size)
        assertTrue(g.calls.zipWithNext().all { (a,b) -> b.second.version > a.second.version })
    }
    @Test fun duplicateVisibleOwnersShareOneDemandUntilLastOwnerLeaves() = runTest {
        val g = Gateway(); val coordinator = TitleSourceCoordinator(backgroundScope, g)
        coordinator.setVisible("row-a",title(1)); coordinator.setVisible("row-b",title(1))
        advanceTimeBy(151); runCurrent(); assertEquals(listOf(title(1)),g.calls.first().second.titles)
        val before = g.calls.size; coordinator.setVisible("row-a",null); advanceTimeBy(200); runCurrent(); assertEquals(before,g.calls.size)
        coordinator.setVisible("row-b",null); advanceTimeBy(151); runCurrent(); assertTrue(g.calls.last().second.titles.isEmpty()); assertTrue(coordinator.hints.value.isEmpty()); coordinator.close()
    }
    @Test fun menuRetainsLastEvidenceWithoutKeepingAHiddenViewAlive() = runTest {
        val g=Gateway();val refresh=kotlinx.coroutines.flow.MutableStateFlow(0L);val c=TitleSourceCoordinator(backgroundScope,g,refresh)
        c.setVisible("card",title(1));advanceTimeBy(151);runCurrent();assertFalse(c.lastKnown.value.isEmpty())
        c.setVisible("card",null);advanceTimeBy(151);runCurrent();assertTrue(c.hints.value.isEmpty());assertFalse(c.lastKnown.value.isEmpty())
        val before=g.calls.size;refresh.value++;advanceTimeBy(20_000);runCurrent();assertEquals(before,g.calls.size)
        c.close();assertTrue(c.lastKnown.value.isEmpty())
    }
    @Test fun explicitCheckRefreshesCurrentViewWithOrderedCleanup() = runTest {
        val g=Gateway();val refresh=kotlinx.coroutines.flow.MutableStateFlow(0L);val c=TitleSourceCoordinator(backgroundScope,g,refresh)
        c.setVisible("card",title(1));advanceTimeBy(151);runCurrent();val before=g.calls.size
        refresh.value++;advanceTimeBy(151);runCurrent();assertEquals(before+2,g.calls.size)
        assertTrue(g.calls[g.calls.lastIndex-1].second.titles.isEmpty());assertEquals(listOf(title(1)),g.calls.last().second.titles)
        assertEquals(1,g.calls.map{it.first}.distinct().size);assertTrue(g.calls.zipWithNext().all{(a,b)->a.second.version<b.second.version});c.close()
    }
    @Test fun coordinatorBoundsVisibleOwnersAndRequests() = runTest {
        val g = Gateway(); val coordinator = TitleSourceCoordinator(backgroundScope,g)
        for (n in 1..300) coordinator.setVisible("owner-$n",title(n))
        advanceTimeBy(151);runCurrent(); assertEquals(24,g.calls.first().second.titles.size)
        for (n in 1..300) coordinator.setVisible("owner-$n",null)
        advanceTimeBy(151);runCurrent(); assertTrue(g.calls.last().second.titles.isEmpty());coordinator.close()
    }
    @Test fun cardMappingExcludesEpisodeLibraryAndActionCards() {
        val base = HomeCard("movie", HomeLink.Details(DetailsTarget("movie","Synthetic",true)),"Synthetic","")
        assertEquals(TitleSourceCoordinate("movie","movie"),base.titleSourceCoordinate())
        assertNull(base.copy(link=HomeLink.Action(HomeAction.SURPRISE)).titleSourceCoordinate())
        assertNull(base.copy(link=HomeLink.Details(base.target!!.copy(fromLibrary=true))).titleSourceCoordinate())
        assertNull(base.copy(episode=CardEpisode(1,1,null,null)).titleSourceCoordinate())
    }
    @Test fun lifecycleWindowPlaybackAndPreparationGateDemand() {
        assertTrue(titleSourceActive(true,true,true,false,false,true))
        for (index in 0..5) {
            val states = mutableListOf(true,true,true,false,false,true)
            states[index] = !states[index]
            assertFalse(titleSourceActive(states[0],states[1],states[2],states[3],states[4],states[5]))
        }
    }
}
