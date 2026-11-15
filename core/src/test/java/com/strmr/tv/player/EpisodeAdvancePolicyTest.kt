package com.strmr.tv.player

import org.junit.Assert.*
import org.junit.Test

class EpisodeAdvancePolicyTest {
    private fun policy(auto: Boolean = true, credits: Boolean = false) = EpisodeAdvancePolicy().apply { autoplay = auto; skipCredits = credits }
    @Test fun `end countdown works without credits skipping and advances once`() {
        val p = policy(); assertTrue(p.end(90_000, 90_000, false))
        assertFalse(p.tick(0, false, true)); assertEquals(5, p.seconds)
        assertFalse(p.tick(4_000, false, true)); assertEquals(1, p.seconds)
        assertTrue(p.tick(5_000, false, true)); assertFalse(p.tick(6_000, false, true)); assertFalse(p.claimAdvance())
    }
    @Test fun `autoplay off suppresses both automatic paths but permits manual play`() {
        val p = policy(auto = false, credits = true)
        assertFalse(p.tick(0, true, true)); assertNull(p.seconds)
        assertTrue(p.end(90_000, 90_000, false)); assertFalse(p.tick(90_000, true, true)); assertNull(p.seconds)
        assertTrue(p.claimAdvance()); assertFalse(p.claimAdvance())
    }
    @Test fun `credits countdown requires both independent preferences`() {
        for (auto in listOf(false, true)) for (credits in listOf(false, true)) {
            val p = policy(auto, credits); p.tick(0, true, true)
            assertEquals(if (auto && credits) 7 else null, p.seconds)
        }
    }
    @Test fun `pause and buffering hold remaining time without catching up`() {
        val p = policy(credits = true); p.tick(0, true, true); p.tick(2_000, true, true)
        assertEquals(5, p.seconds); p.tick(90_000, true, false); assertEquals(5, p.seconds)
        p.tick(91_000, true, true); assertEquals(4, p.seconds)
        p.togglePause(); p.tick(92_000, true, true); p.tick(120_000, true, true); assertEquals(4, p.seconds)
        p.togglePause(); p.tick(121_000, true, true); assertEquals(4, p.seconds)
        assertTrue(p.tick(125_000, true, true))
    }
    @Test fun `dismissal survives repeated end events and preference changes`() {
        val p = policy(credits = true); p.tick(0, true, true); p.dismiss()
        assertFalse(p.end(90_000, 90_000, false)); p.autoplay = false; p.autoplay = true
        assertFalse(p.tick(90_000, true, true)); assertNull(p.seconds)
    }
    @Test fun `seeking out of credits cancels automation for this episode`() {
        val p = policy(credits = true); p.tick(0, true, true); p.tick(1_000, false, true)
        assertNull(p.seconds); p.tick(2_000, true, true); assertNull(p.seconds)
        assertTrue(p.end(90_000, 90_000, false)); assertFalse(p.tick(90_000, true, true)); assertNull(p.seconds)
        assertTrue(p.claimAdvance())
    }
    @Test fun `errors premature ends and unknown duration never start countdown`() {
        for (values in listOf(Triple(80_000L,90_000L,false),Triple(90_000L,90_000L,true),Triple(0L,0L,false))) {
            val p = policy(); assertFalse(p.end(values.first, values.second, values.third))
            p.tick(0, false, true); assertNull(p.seconds)
        }
    }
    @Test fun `cancellation invalidates late ends and manual actions`() {
        val p = policy(credits = true); p.tick(0, true, true); p.cancel()
        assertFalse(p.end(90_000, 90_000, false)); assertFalse(p.tick(100_000, true, true)); assertFalse(p.claimAdvance())
    }
    @Test fun `turning autoplay off clears an active countdown`() {
        val p = policy(); p.end(90_000, 90_000, false); p.tick(0, false, true)
        p.autoplay = false; assertFalse(p.tick(5_000, false, true)); assertNull(p.seconds)
    }
    @Test fun `repeated end shortens existing countdown without resetting it`() {
        val p = policy(credits = true); p.tick(0, true, true); p.tick(4_000, true, true)
        p.end(90_000, 90_000, false); p.tick(4_250, true, true); assertEquals(3, p.seconds)
        p.end(90_000, 90_000, false); assertTrue(p.tick(7_000, true, true))
    }
    @Test fun `seeking back after end cancels the end countdown`() {
        val p = policy(); p.end(90_000,90_000,false); p.tick(0,false,true)
        p.observePosition(20_000,90_000); assertFalse(p.mediaEnded); assertNull(p.seconds)
        p.end(90_000,90_000,false); assertFalse(p.tick(90_000,false,true)); assertNull(p.seconds)
    }
    @Test fun `a backwards clock does not increase remaining time`() {
        val p = policy(); p.end(90_000,90_000,false); p.tick(1_000,false,true); p.tick(500,false,true)
        assertEquals(5,p.seconds)
    }
}
