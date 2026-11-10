package com.strmr.mobile.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeTimerTest {
    @Test
    fun hidesAfterTheDelayWhilePlaying() {
        val timer = ChromeTimer(hideAfterMs = 4_000)
        timer.show(0)
        assertFalse(timer.shouldHide(3_999, playing = true, busy = false))
        assertTrue(timer.shouldHide(4_000, playing = true, busy = false))
    }

    @Test
    fun staysWhilePausedOrBusy() {
        val timer = ChromeTimer(hideAfterMs = 4_000)
        timer.show(0)
        assertFalse(timer.shouldHide(10_000, playing = false, busy = false))
        assertFalse(timer.shouldHide(10_000, playing = true, busy = true))
    }

    @Test
    fun aTouchRestartsTheCountdown() {
        val timer = ChromeTimer(hideAfterMs = 4_000)
        timer.show(0)
        timer.touched(3_000)
        assertFalse(timer.shouldHide(6_000, playing = true, busy = false))
        assertTrue(timer.shouldHide(7_000, playing = true, busy = false))
    }

    @Test
    fun tapTogglesAndHiddenNeverHidesAgain() {
        val timer = ChromeTimer()
        timer.toggle(0)
        assertFalse(timer.visible)
        assertFalse(timer.shouldHide(100_000, playing = true, busy = false))
        timer.toggle(1_000)
        assertTrue(timer.visible)
    }
}
