package com.strmr.tv.library

import com.strmr.tv.home.CustomList
import com.strmr.tv.home.ShelfConfig
import com.strmr.tv.home.WatchlistItem
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileListIsolationTest {
    private class Store(
        val reset: () -> Unit,
        val refresh: suspend (suspend () -> String) -> Unit,
        val value: () -> String?,
    )

    private val stores get() = listOf(
        Store(CustomListsStore::reset, { read -> CustomListsStore.refresh { listOf(CustomList(read(), "Synthetic list")) } }, { CustomListsStore.lists?.firstOrNull()?.id }),
        Store(WatchlistStore::reset, { read -> WatchlistStore.refresh { listOf(WatchlistItem(read())) } }, { WatchlistStore.items.firstOrNull()?.id }),
        Store(HiddenItemsStore::reset, { read -> HiddenItemsStore.refresh { listOf(HiddenItem(read())) } }, { HiddenItemsStore.items.firstOrNull()?.id }),
        Store(SetAsideStore::reset, { read -> SetAsideStore.refresh { listOf(WatchlistItem(read())) } }, { SetAsideStore.items.firstOrNull()?.id }),
        Store(ListsTabStore::reset, { read -> ListsTabStore.refresh { ListsTabSnapshot(shelves = listOf(ShelfConfig(read()))) } }, { ListsTabStore.shelves?.firstOrNull()?.id }),
    )

    @Before fun setUp() { ProfileDataContext.invalidate(); stores.forEach { it.reset() } }
    @After fun tearDown() { ProfileDataContext.invalidate(); stores.forEach { it.reset() } }

    @Test fun lateOldProfileReplyCannotReplaceNewProfileLists() = runTest {
        for (store in stores) {
            val old = CompletableDeferred<String>()
            val first = launch { store.refresh { old.await() } }; runCurrent()
            ProfileDataContext.invalidate(); store.reset()
            store.refresh { "new-profile" }
            old.complete("old-private-title"); first.join()
            assertEquals("new-profile", store.value())
        }
    }

    @Test fun resetAtTheSameProfileDetachesOldWorkAndAllowsANewRequest() = runTest {
        for (store in stores) {
            val old = CompletableDeferred<String>()
            val first = launch { store.refresh { old.await() } }; runCurrent()
            store.reset(); store.refresh { "new-load" }
            old.complete("stale-load"); first.join()
            assertEquals("new-load", store.value())
        }
    }

    @Test fun returningToTheOriginalProfileDoesNotReviveItsOldReply() = runTest {
        for (store in stores) {
            val old = CompletableDeferred<String>()
            val first = launch { store.refresh { old.await() } }; runCurrent()
            ProfileDataContext.invalidate(); store.reset()
            ProfileDataContext.invalidate(); store.reset()
            store.refresh { "profile-a-new-visit" }
            old.complete("profile-a-before-switch"); first.join()
            assertEquals("profile-a-new-visit", store.value())
        }
    }

    @Test fun oldCompletionCannotDetachTheNewProfilesSharedFlight() = runTest {
        for (store in stores) {
            val old = CompletableDeferred<String>(); val fresh = CompletableDeferred<String>()
            val first = launch { store.refresh { old.await() } }; runCurrent()
            ProfileDataContext.invalidate(); store.reset()
            val second = launch { store.refresh { fresh.await() } }; runCurrent()
            old.complete("old"); first.join()
            var duplicateRequests = 0
            val joined = launch { store.refresh { duplicateRequests++; "unwanted-duplicate" } }; runCurrent()
            assertEquals(0, duplicateRequests)
            fresh.complete("current"); second.join(); joined.join()
            assertEquals("current", store.value())
        }
    }

    @Test fun cancelledWaiterDoesNotCancelTheOwningRequest() = runTest {
        for (store in stores) {
            val reply = CompletableDeferred<String>()
            val owner = launch { store.refresh { reply.await() } }; runCurrent()
            val waiter = launch { store.refresh { fail("Duplicate request"); "" } }; runCurrent()
            waiter.cancel(); waiter.join()
            assertTrue(owner.isActive)
            reply.complete("current"); owner.join(); assertEquals("current", store.value())
        }
    }

    @Test fun cancellationPropagatesAndDoesNotMarkAnUnloadedWatchlistLoaded() = runTest {
        try {
            WatchlistStore.refresh { throw CancellationException("cancelled") }
            fail("Cancellation was hidden")
        } catch (_: CancellationException) { }
        assertFalse(WatchlistStore.isLoaded)
        assertNull(WatchlistStore.loadError)
        WatchlistStore.refresh { listOf(WatchlistItem("retry")) }
        assertTrue(WatchlistStore.isLoaded)
        assertEquals("retry", WatchlistStore.items.single().id)
    }

    @Test fun lateWatchlistFailureDoesNotPublishAnErrorOrLoadedFlagForTheNewProfile() = runTest {
        val reply = CompletableDeferred<List<WatchlistItem>>()
        val old = launch { WatchlistStore.refresh { reply.await() } }; runCurrent()
        ProfileDataContext.invalidate(); WatchlistStore.reset()
        reply.completeExceptionally(IOException("private old diagnostic")); old.join()
        assertFalse(WatchlistStore.isLoaded); assertNull(WatchlistStore.loadError); assertTrue(WatchlistStore.items.isEmpty())
    }

    @Test fun watchlistRetryKeepsExistingTitlesOnFailureAndClearsTheErrorOnSuccess() = runTest {
        WatchlistStore.refresh { throw IOException("private diagnostic") }
        assertEquals("Could not load your watchlist. Try again.", WatchlistStore.loadError)
        WatchlistStore.refresh { listOf(WatchlistItem("kept")) }
        assertNull(WatchlistStore.loadError)
        WatchlistStore.refresh { throw IOException("private diagnostic") }
        assertEquals("kept", WatchlistStore.items.single().id); assertNull(WatchlistStore.loadError)
    }

    @Test fun aContextChangeWithoutResetStillDiscardsOutstandingReadResults() = runTest {
        for (store in stores) {
            val reply = CompletableDeferred<String>()
            val job = launch { store.refresh { reply.await() } }; runCurrent()
            ProfileDataContext.invalidate()
            reply.complete("private-stale-result"); job.join(); assertNull(store.value())
        }
    }

    @Test fun currentContextCanStartImmediatelyWithoutWaitingForOldWork() = runTest {
        for (store in stores) {
            val reply = CompletableDeferred<String>()
            val job = launch { store.refresh { reply.await() } }; runCurrent()
            ProfileDataContext.invalidate()
            val fresh = async { store.refresh { "new" } }; runCurrent()
            assertTrue(fresh.isCompleted); assertEquals("new", store.value())
            reply.complete("old"); job.join(); assertEquals("new", store.value())
        }
    }

    @Test fun partialListTabLoadPreservesCurrentContextRowsOnly() = runTest {
        ListsTabStore.refresh { ListsTabSnapshot(shelves = listOf(ShelfConfig("existing")), continueWatching = emptyList()) }
        ListsTabStore.refresh { ListsTabSnapshot(libraries = emptyList()) }
        assertEquals("existing", ListsTabStore.shelves!!.single().id)
        assertNotNull(ListsTabStore.continueWatching)
        ProfileDataContext.invalidate(); ListsTabStore.reset()
        ListsTabStore.refresh { ListsTabSnapshot(libraries = emptyList()) }
        assertNull(ListsTabStore.shelves); assertNull(ListsTabStore.continueWatching)
    }

    @Test fun independentRequestsAreSharedOnlyWithinOneLiveContext() = runTest {
        var context = 0L; var reads = 0; val gate = ProfileRefresh { context }
        val firstReply = CompletableDeferred<String>(); val secondReply = CompletableDeferred<String>(); val received = mutableListOf<String>()
        val first = launch { gate.run({ reads++; firstReply.await() }, received::add) }; runCurrent()
        context++
        val second = launch { gate.run({ reads++; secondReply.await() }, received::add) }; runCurrent()
        firstReply.complete("private-first"); first.join()
        secondReply.complete("second"); second.join()
        assertEquals(2, reads); assertEquals(listOf("second"), received)
    }

    @Test fun mutationAcknowledgementGuardRejectsEveryInvalidatedContext() {
        val old = ProfileDataContext.current; ProfileDataContext.requireCurrent(old)
        repeat(2) { ProfileDataContext.invalidate() }
        try { ProfileDataContext.requireCurrent(old); fail("Old operation became current again") } catch (_: CancellationException) { }
    }
}
