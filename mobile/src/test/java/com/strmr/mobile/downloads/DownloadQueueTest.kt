package com.strmr.mobile.downloads

import com.strmr.mobile.downloads.DownloadItem.Status
import com.strmr.mobile.downloads.DownloadItem.WaitingReason
import com.strmr.tv.api.DetailsTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadQueueTest {
    private val movie = DetailsTarget("tmdb:movie:1", "Movie", isMovie = true)

    private fun item(id: String, status: Status, created: Long, retryAt: Long? = null) =
        DownloadItem.create(movie, null, "/m/$id.mkv", null, 100, now = created).copy(id = id, status = status, retryAt = retryAt)

    private fun conditions(max: Int = 1, wifiOnly: Boolean = false, unmetered: Boolean = true, reachable: Boolean = true, now: Long = 1000) =
        DownloadQueue.Conditions(max, wifiOnly, unmetered, reachable, now)

    @Test fun startsOldestPendingUpToTheLimit() {
        val items = listOf(item("b", Status.PENDING, 20), item("a", Status.PENDING, 10), item("c", Status.PENDING, 30))
        assertEquals(listOf("a", "b"), DownloadQueue.plan(items, conditions(max = 2)).start)
    }

    @Test fun runningDownloadsTakeSlots() {
        val items = listOf(item("a", Status.DOWNLOADING, 10), item("b", Status.PENDING, 20))
        assertEquals(emptyList<String>(), DownloadQueue.plan(items, conditions(max = 1)).start)
        assertEquals(listOf("b"), DownloadQueue.plan(items, conditions(max = 2)).start)
    }

    @Test fun wifiOnlyOnMeteredHoldsEverything() {
        val items = listOf(item("a", Status.DOWNLOADING, 10), item("b", Status.PENDING, 20), item("c", Status.PAUSED, 5))
        val plan = DownloadQueue.plan(items, conditions(wifiOnly = true, unmetered = false))
        assertEquals(listOf("a"), plan.hold)
        assertEquals(emptyList<String>(), plan.start)
        assertEquals(mapOf("a" to WaitingReason.WIFI, "b" to WaitingReason.WIFI), plan.waiting)
    }

    @Test fun unreachableServerHolds() {
        val plan = DownloadQueue.plan(listOf(item("a", Status.PENDING, 1)), conditions(reachable = false))
        assertEquals(WaitingReason.SERVER, plan.waiting["a"])
        assertEquals(emptyList<String>(), plan.start)
    }

    @Test fun retriesWaitUntilDue() {
        val items = listOf(item("a", Status.PENDING, 1, retryAt = 5000), item("b", Status.PENDING, 2))
        val plan = DownloadQueue.plan(items, conditions(now = 1000))
        assertEquals(listOf("b"), plan.start)
        assertEquals(WaitingReason.RETRY, plan.waiting["a"])
        assertEquals(5000L, plan.nextRetryAt)
    }

    @Test fun failuresRetryWithBackoffThenFail() {
        var x = item("a", Status.DOWNLOADING, 1)
        DownloadQueue.retryDelays.forEachIndexed { i, delay ->
            x = DownloadQueue.afterFailure(x, "lost", retryable = true, now = 0)
            assertEquals(Status.PENDING, x.status)
            assertEquals(i + 1, x.retries)
            assertEquals(delay * 1000, x.retryAt)
            x = x.copy(status = Status.DOWNLOADING)
        }
        assertEquals(Status.FAILED, DownloadQueue.afterFailure(x, "lost", true, 0).status)
        assertEquals(Status.FAILED, DownloadQueue.afterFailure(item("b", Status.DOWNLOADING, 1), "404", false, 0).status)
    }

    @Test fun pauseResumeTransitions() {
        val running = item("a", Status.DOWNLOADING, 1).copy(retries = 3)
        val paused = DownloadQueue.paused(running)!!
        assertEquals(Status.PAUSED, paused.status)
        assertNull(DownloadQueue.paused(paused))
        val resumed = DownloadQueue.resumed(paused)!!
        assertEquals(Status.PENDING, resumed.status)
        assertEquals(0, resumed.retries)
        assertNull(DownloadQueue.resumed(item("c", Status.COMPLETED, 1)))
        assertEquals(Status.PENDING, DownloadQueue.relaunched(running).status)
        assertEquals(Status.COMPLETED, DownloadQueue.relaunched(item("d", Status.COMPLETED, 1)).status)
    }
}
