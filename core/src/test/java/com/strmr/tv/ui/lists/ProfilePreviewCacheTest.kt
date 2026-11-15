package com.strmr.tv.ui.lists

import com.strmr.tv.home.BrowseEntry
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.BrowsePage
import com.strmr.tv.home.WatchlistItem
import com.strmr.tv.library.ProfileDataContext
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfilePreviewCacheTest {
    private fun art(label: String) = TileArt(posters = listOf(label))
    @After fun cleanup() { BrowsePreviews.reset(); ProfileDataContext.invalidate() }

    @Test fun sameProfileNumberOnAnotherServerOrAccountDoesNotReuseArtwork() = runTest {
        val cache = ProfilePreviewCache(); var loads = 0
        repeat(3) { visit ->
            if (visit > 0) ProfileDataContext.invalidate()
            val expected = art("context-$visit")
            assertEquals(expected, cache.art("same-list", null) { loads++; expected })
            assertEquals(expected, cache.art("same-list", null) { fail("Expected cache hit"); null })
        }
        assertEquals(3, loads)
    }

    @Test fun aLateResponseNeitherReturnsPrivateArtworkNorPoisonsTheNewCache() = runTest {
        val cache = ProfilePreviewCache(); val reply = CompletableDeferred<TileArt>()
        val old = async { cache.art("list", 1) { reply.await() } }; runCurrent()
        ProfileDataContext.invalidate()
        assertEquals(art("new"), cache.art("list", 1) { art("new") })
        reply.complete(art("old-private"))
        assertEquals(TileArt(), old.await())
        assertEquals(art("new"), cache.art("list", 1) { fail("Current cache lost"); null })
    }

    @Test fun resetWhileLoadingDiscardsEvenASameContextReply() = runTest {
        val cache = ProfilePreviewCache(); val reply = CompletableDeferred<TileArt>()
        val old = async { cache.art("list", 1) { reply.await() } }; runCurrent()
        cache.reset(); reply.complete(art("before-reset"))
        assertEquals(TileArt(), old.await())
        assertEquals(art("retry"), cache.art("list", 1) { art("retry") })
    }

    @Test fun expiredPreviewReloadsAndVersionChangesDoNotUseThePreviousSelection() = runTest {
        var clock = 0L; val cache = ProfilePreviewCache(now = { clock }, maxAgeMs = 10)
        cache.art("list", 1) { art("old") }; clock = 10
        assertEquals(art("fresh"), cache.art("list", 1) { art("fresh") })
        assertEquals(art("version-two"), cache.art("list", 2) { art("version-two") })
    }

    @Test fun leastRecentlyUsedPreviewsAreEvictedAtTheBound() = runTest {
        val cache = ProfilePreviewCache(capacity = 2)
        cache.art("a", null) { art("a") }; cache.art("b", null) { art("b") }
        cache.art("a", null) { fail("Expected cache hit"); null }
        cache.art("c", null) { art("c") }
        var reloaded = false
        assertEquals(art("b-new"), cache.art("b", null) { reloaded = true; art("b-new") })
        assertTrue(reloaded)
    }

    @Test fun failuresStayRetryableAndCancellationIsNeverCached() = runTest {
        val cache = ProfilePreviewCache()
        assertEquals(TileArt(), cache.art("list", null) { throw IOException("private diagnostic") })
        try { cache.art("list", null) { throw CancellationException() }; fail("Cancellation hidden") } catch (_: CancellationException) { }
        assertEquals(art("retry"), cache.art("list", null) { art("retry") })
    }

    @Test fun actualBrowsePreviewsRejectsLateArtworkAndReloadsAfterReset() = runTest {
        BrowsePreviews.reset()
        val reply = CompletableDeferred<BrowsePage>()
        val old = async { BrowsePreviews.art(BrowseList.Watchlist, 1) { reply.await() } }; runCurrent()
        ProfileDataContext.invalidate(); BrowsePreviews.reset()
        val fresh = BrowsePage(listOf(BrowseEntry.ItemEntry(WatchlistItem("fresh", posterUrl = "synthetic-fresh-art"))), 1)
        val expected = BrowsePreviews.art(BrowseList.Watchlist, 1) { fresh }
        reply.complete(BrowsePage(listOf(BrowseEntry.ItemEntry(WatchlistItem("old", posterUrl = "synthetic-private-art"))), 1))
        assertEquals(TileArt(), old.await())
        assertEquals(listOf("synthetic-fresh-art"), expected.posters)
        assertEquals(expected, BrowsePreviews.art(BrowseList.Watchlist, 1) { fail("Expected current cache"); fresh })
    }

    @Test fun invalidCacheBoundsAreRejected() {
        for (create in listOf({ ProfilePreviewCache(capacity = 0) }, { ProfilePreviewCache(maxAgeMs = 0) })) {
            try { create(); fail("Unbounded or expired cache allowed") } catch (_: IllegalArgumentException) { }
        }
    }
}
