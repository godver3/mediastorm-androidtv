package com.strmr.tv.player

import com.strmr.tv.live.LiveStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Stremio failover's choice of the next stream (Expo `nextUntriedStremioStreamIndex`). */
class StremioFailoverTest {
    @Test fun walksUntriedStreamsInAddonOrder() {
        val failover = StremioFailover().apply { tuned(2, listOf(0, 2, 5)) }
        assertEquals(0, failover.next())
        assertEquals(5, failover.next())
        assertNull(failover.next())
    }

    @Test fun serverPickCountsAsTheFirstStream() {
        // A direct stream reports no index: the server tuned the first playable one.
        val failover = StremioFailover().apply { tuned(null, null) }
        assertNull("nothing to try until the list is known", failover.next())
        failover.learn(listOf(1, 4))
        assertEquals(4, failover.next())
        assertNull(failover.next())
    }

    @Test fun pickedStreamIsNotRetriedAfterLearningTheList() {
        val failover = StremioFailover().apply { tuned(4, null) }
        failover.learn(listOf(1, 4, 7))
        assertEquals(1, failover.next())
        assertEquals(7, failover.next())
        assertNull(failover.next())
    }

    @Test fun laterTunesKeepTheKnownList() {
        val failover = StremioFailover().apply { tuned(0, listOf(0, 1)); tuned(1, null) }
        assertNull(failover.next())
    }

    @Test fun liveStreamDefaultsCarryNoStremioStreams() {
        val stream = LiveStream("http://server/a.m3u8", "a")
        assertNull(stream.stremioStreamIndex)
        assertNull(stream.stremioStreamIndexes)
    }
}
