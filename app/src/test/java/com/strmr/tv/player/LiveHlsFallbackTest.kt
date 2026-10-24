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
    fun aRetunedDirectStreamThatNeverPlaysFallsBack() {
        val hls = LiveHlsFallback(direct = true).apply { playing() }
        hls.tuned(direct = true)
        assertTrue(hls.failed())
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
