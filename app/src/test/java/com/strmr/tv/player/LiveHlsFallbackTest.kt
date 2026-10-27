package com.strmr.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveHlsFallbackTest {
    @Test
    fun directStreamThatNeverPlayedFallsBackOnce() {
        val hls = LiveHlsFallback(direct = true)
        assertFalse(hls.forcesHls)
        assertTrue(hls.failed())
        assertTrue(hls.forcesHls)
        hls.tuned(direct = false)
        // The HLS session failing too is the channel's failure, not another fallback.
        assertFalse(hls.failed())
    }

    @Test
    fun noFallbackForHlsOrAStreamThatPlayed() {
        assertFalse(LiveHlsFallback(direct = false).failed())
        val played = LiveHlsFallback(direct = true).apply { playing() }
        assertFalse(played.failed())
        assertFalse(played.forcesHls)
    }

    @Test
    fun aRetunedDirectStreamThatNeverPlaysDoesNotFallBackOnceDirectPlayed() {
        val hls = LiveHlsFallback(direct = true)
        assertTrue(hls.playing())
        assertTrue(hls.directWorked)
        assertFalse(hls.playing())
        // Direct is known to work for the source: a retune that never plays is the provider's failure.
        hls.tuned(direct = true)
        assertFalse(hls.failed())
    }

    @Test
    fun noFallbackOnceDirectHasPlayedForTheSource() {
        val hls = LiveHlsFallback(direct = true).apply { directWorked = true }
        assertFalse(hls.failed())
        assertFalse(hls.forcesHls)
    }

    @Test
    fun theFallbackPlayingIsNotADirectStream() {
        val hls = LiveHlsFallback(direct = true)
        hls.failed()
        hls.tuned(direct = false)
        assertFalse(hls.playing())
        assertFalse(hls.directWorked)
    }

    @Test
    fun aStalledFallbackIsNotConfirmed() {
        val hls = LiveHlsFallback(direct = true)
        hls.failed()
        hls.tuned(direct = false)
        hls.playing()
        hls.stalled()
        assertFalse(hls.confirm())
        assertEquals(LiveHlsFallback.Mode.FALLBACK, hls.mode)
    }

    @Test
    fun confirmedOnceAfterTheFallbackPlays() {
        val hls = LiveHlsFallback(direct = true)
        assertFalse(hls.confirm())
        hls.failed()
        hls.tuned(direct = false)
        assertFalse(hls.confirm())
        hls.playing()
        assertTrue(hls.confirm())
        assertFalse(hls.confirm())
        assertEquals(LiveHlsFallback.Mode.CONFIRMED, hls.mode)
        assertTrue(hls.forcesHls)
    }
}
