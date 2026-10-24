package com.strmr.tv.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiscreenSessionTest {
    private fun channel(id: String, source: String? = "s1") = LiveChannel(id, "Channel $id", "http://provider/$id", sourceId = source)

    /** Records calls; starts can be held open with [gates] and made to fail with [failStarts] / [failStops]. */
    private class FakeTransport : MultiscreenSession.Transport {
        val calls = mutableListOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val failStarts = mutableSetOf<String>()
        val failStops = mutableSetOf<String>()
        var emptyUrl = false
        private var next = 0

        override suspend fun start(channel: LiveChannel): LiveStream {
            calls += "start ${channel.id}"
            gates[channel.id]?.await()
            if (channel.id in failStarts) throw IllegalStateException("no capacity")
            val id = "session-${channel.id}-${next++}"
            return LiveStream(if (emptyUrl) "" else "http://server/$id.m3u8", id)
        }

        override suspend fun stop(sessionId: String) {
            calls += "stop $sessionId"
            if (sessionId in failStops) throw IllegalStateException("stop failed")
        }
    }

    private fun TestScope.session(transport: FakeTransport, max: Int = MultiscreenSession.MAX_SCREENS) =
        MultiscreenSession(transport, backgroundScope, max)

    private val MultiscreenSession.slots get() = state.value.slots

    @Test fun startsWithTwoEmptySlots() = runTest {
        val s = session(FakeTransport())
        assertEquals(2, s.slots.size)
        assertTrue(s.slots.none { it.isOccupied })
    }

    @Test fun tuneMarksLoadingThenPlays() = runTest {
        val t = FakeTransport()
        val s = session(t)
        val job = s.tune(0, channel("a"))
        assertTrue(s.slots[0].loading)
        job.await()
        val slot = s.slots[0]
        assertFalse(slot.loading)
        assertEquals("a", slot.channel?.id)
        assertEquals("session-a-0", slot.sessionId)
        assertEquals("http://server/session-a-0.m3u8", slot.source?.url)
    }

    @Test fun duplicateChannelFocusesItsSlot() = runTest {
        val t = FakeTransport()
        val s = session(t)
        s.tune(0, channel("a")).await()
        s.tune(1, channel("a")).await()
        assertEquals(0, s.state.value.focusedIndex)
        assertFalse(s.slots[1].isOccupied)
        assertEquals(listOf("start a"), t.calls)
    }

    @Test fun duplicateCountsInFlightRequests() = runTest {
        val t = FakeTransport().apply { gates["a"] = CompletableDeferred() }
        val s = session(t)
        s.tune(0, channel("a"))
        runCurrent()
        s.focus(1)
        s.tune(1, channel("a"))
        assertEquals(0, s.state.value.focusedIndex)
        assertEquals(0, s.indexOf(channel("a")))
        t.gates["a"]!!.complete(Unit)
    }

    @Test fun sameIdOnAnotherSourceIsNotADuplicate() = runTest {
        val s = session(FakeTransport())
        s.tune(0, channel("a", "s1")).await()
        s.tune(1, channel("a", "s2")).await()
        assertEquals("s2", s.slots[1].channel?.sourceId)
    }

    @Test fun retuneReleasesTheOldSessionBeforeStarting() = runTest {
        val t = FakeTransport()
        val s = session(t)
        s.tune(0, channel("a")).await()
        s.tune(0, channel("b")).await()
        assertEquals(listOf("start a", "stop session-a-0", "start b"), t.calls)
        assertEquals("b", s.slots[0].channel?.id)
    }

    @Test fun staleTuneGivesWayToTheNewerOne() = runTest {
        val t = FakeTransport().apply { gates["a"] = CompletableDeferred() }
        val s = session(t)
        val first = s.tune(0, channel("a"))
        runCurrent()
        val second = s.tune(0, channel("b"))
        t.gates["a"]!!.complete(Unit)
        first.await()
        second.await()
        // a's session, started after it was superseded, is stopped; b plays.
        assertEquals(listOf("start a", "stop session-a-0", "start b"), t.calls)
        assertEquals("b", s.slots[0].channel?.id)
        assertNotNull(s.slots[0].source)
    }

    @Test fun startsAreSerialized() = runTest {
        val t = FakeTransport().apply { gates["a"] = CompletableDeferred() }
        val s = session(t)
        s.tune(0, channel("a"))
        s.tune(1, channel("b"))
        runCurrent()
        assertEquals(listOf("start a"), t.calls)
        t.gates["a"]!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("start a", "start b"), t.calls)
    }

    @Test fun failedStartShowsTheTuneError() = runTest {
        val t = FakeTransport().apply { failStarts += "a" }
        val s = session(t)
        s.tune(0, channel("a")).await()
        assertEquals(MultiscreenSession.TUNE_FAILED, s.slots[0].error)
        assertEquals("a", s.slots[0].channel?.id)
        assertFalse(s.slots[0].loading)
        assertEquals(-1, s.indexOf(channel("b")))
    }

    @Test fun emptyStreamIsReleasedAndFails() = runTest {
        val t = FakeTransport().apply { emptyUrl = true }
        val s = session(t)
        s.tune(0, channel("a")).await()
        assertEquals(listOf("start a", "stop session-a-0"), t.calls)
        assertEquals(MultiscreenSession.TUNE_FAILED, s.slots[0].error)
        assertNull(s.slots[0].source)
    }

    @Test fun failOnlyForTheCurrentSource() = runTest {
        val s = session(FakeTransport())
        s.tune(0, channel("a")).await()
        val old = s.slots[0].source!!
        s.tune(0, channel("b")).await()
        s.fail(0, old)
        assertNull(s.slots[0].error)
        s.fail(0, s.slots[0].source!!)
        assertEquals(MultiscreenSession.STREAM_FAILED, s.slots[0].error)
    }

    @Test fun removeMovesAudioToTheFirstPlayingSlot() = runTest {
        val t = FakeTransport()
        val s = session(t)
        s.tune(0, channel("a")).await()
        s.tune(1, channel("b")).await()
        s.listen(0)
        s.remove(0).await()
        assertFalse(s.slots[0].isOccupied)
        assertEquals(1, s.state.value.audioIndex)
        assertTrue("stop session-a-0" in t.calls)
    }

    @Test fun removeKeepsTheSlotWhenItsStopFails() = runTest {
        val t = FakeTransport()
        val s = session(t)
        s.tune(0, channel("a")).await()
        t.failStops += "session-a-0"
        s.remove(0).await()
        assertEquals(MultiscreenSession.RELEASE_FAILED, s.slots[0].error)
        assertEquals("session-a-0", s.slots[0].sessionId)
    }

    @Test fun listenNeedsASource() = runTest {
        val s = session(FakeTransport())
        s.tune(0, channel("a")).await()
        s.listen(1)
        assertEquals(0, s.state.value.audioIndex)
        s.tune(1, channel("b")).await()
        s.listen(1)
        assertEquals(1, s.state.value.audioIndex)
    }

    @Test fun addsSlotsUpToTheCap() = runTest {
        val s = session(FakeTransport())
        for ((i, id) in listOf("a", "b", "c", "d", "e").withIndex()) s.tune(i, channel(id)).await()
        assertEquals(4, s.slots.size)
        assertEquals(-1, s.indexOf(channel("e")))
        // Only one past the end adds a slot.
        val small = session(FakeTransport(), max = 3)
        small.tune(3, channel("x")).await()
        assertEquals(2, small.slots.size)
    }

    @Test fun adoptTakesOverTheFirstScreen() = runTest {
        val t = FakeTransport()
        val s = session(t)
        s.adopt(channel("a"), LiveStream("http://server/player.m3u8", "player-session"))
        assertEquals("player-session", s.slots[0].sessionId)
        assertEquals("http://server/player.m3u8", s.slots[0].source?.url)
        s.close()
        assertEquals(listOf("stop player-session"), t.calls)
    }

    @Test fun closeReleasesEverySessionAndSupersedesPendingTunes() = runTest {
        val t = FakeTransport().apply { gates["b"] = CompletableDeferred() }
        val s = session(t)
        s.tune(0, channel("a")).await()
        s.tune(1, channel("b"))
        runCurrent()
        t.gates["b"]!!.complete(Unit)
        s.close()
        assertEquals(listOf("start a", "start b", "stop session-b-1", "stop session-a-0"), t.calls)
        assertTrue(s.slots.none { it.sessionId != null })
        // Closed: no more tunes.
        s.tune(0, channel("c")).await()
        assertFalse("start c" in t.calls)
    }

    @Test fun closeThrowsWhenAReleaseFailsAndRetriesOnlyThatOne() = runTest {
        val t = FakeTransport()
        val s = session(t)
        s.tune(0, channel("a")).await()
        s.tune(1, channel("b")).await()
        t.failStops += "session-b-1"
        try {
            s.close()
            fail("close should throw")
        } catch (_: IllegalStateException) {
        }
        assertNull(s.slots[0].sessionId)
        assertEquals("session-b-1", s.slots[1].sessionId)
        t.failStops.clear()
        t.calls.clear()
        s.close()
        assertEquals(listOf("stop session-b-1"), t.calls)
    }

    @Test fun sourcesCompareByIdentity() = runTest {
        val s = session(FakeTransport())
        s.tune(0, channel("a")).await()
        val first = s.slots[0].source!!
        s.tune(0, channel("b")).await()
        s.tune(0, channel("a")).await()
        assertFalse(first === s.slots[0].source)
        assertSame(s.slots[0].source, s.state.value.slots[0].source)
        backgroundScope.cancel()
    }
}
