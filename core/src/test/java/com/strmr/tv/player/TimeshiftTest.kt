package com.strmr.tv.player

import com.strmr.tv.live.LiveStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeshiftTest {
    @Test
    fun timeshiftLiveMaySlowDownToBuildALeadButNeverSpeedsUp() {
        val config = Timeshift.liveConfiguration
        // Never above 1x: a resumed pause stays where the viewer left it.
        assertEquals(1f, config.maxPlaybackSpeed)
        // Below 1x, so a tune that joined at the very edge builds a buffer instead of stalling.
        assertTrue(config.minPlaybackSpeed < 1f)
        assertEquals(Timeshift.MIN_LIVE_SPEED, config.minPlaybackSpeed)
    }

    @Test
    fun behindLiveIsDistanceFromDefaultPosition() {
        assertEquals(0, Timeshift.behindLiveMs(defaultPositionMs = 1_800_000, positionMs = 1_800_000))
        assertEquals(300_000, Timeshift.behindLiveMs(defaultPositionMs = 1_800_000, positionMs = 1_500_000))
        // Past the default position (the window's tail) is still live.
        assertEquals(0, Timeshift.behindLiveMs(defaultPositionMs = 1_800_000, positionMs = 1_820_000))
    }

    @Test
    fun liveWithinTolerance() {
        assertTrue(Timeshift.isAtLive(0))
        assertTrue(Timeshift.isAtLive(Timeshift.AT_LIVE_TOLERANCE_MS))
        assertFalse(Timeshift.isAtLive(Timeshift.AT_LIVE_TOLERANCE_MS + 1))
    }

    @Test
    fun timeshiftNeedsAnHlsSessionAndAWindow() {
        assertTrue(LiveStream("u", "s", timeshiftWindowSeconds = 1800).timeshift)
        assertFalse(LiveStream("u", "s", timeshiftWindowSeconds = 0).timeshift)
        assertFalse(LiveStream("u", null, timeshiftWindowSeconds = 1800).timeshift)
    }
}
