package com.strmr.mobile.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMathTest {
    @Test
    fun doubleTapsOnOneSideAddUpWithinTheWindow() {
        val ripple = SeekRipple()
        assertEquals(10_000L, ripple.tap(1, 1_000))
        assertEquals(10_000L, ripple.tap(1, 1_500))
        assertEquals(20_000L, ripple.totalMs)
        assertEquals("+20 s", ripple.label)
        assertTrue(ripple.showing(2_000))
        assertFalse(ripple.showing(2_400))
    }

    @Test
    fun aTapAfterTheWindowOrOnTheOtherSideStartsOver() {
        val ripple = SeekRipple()
        ripple.tap(1, 0)
        assertEquals(-10_000L, ripple.tap(-1, 200))
        assertEquals(-10_000L, ripple.totalMs)
        assertEquals("−10 s", ripple.label)
        ripple.tap(-1, 5_000)
        assertEquals(-10_000L, ripple.totalMs)
    }

    @Test
    fun sideIsTheHalfTouched() {
        assertEquals(-1, sideOf(10f, 100f))
        assertEquals(1, sideOf(60f, 100f))
    }

    @Test
    fun swipingUpRaisesTheLevelAndClamps() {
        // Up (negative y) half the height at 1.2× raises by 0.6.
        assertEquals(0.8f, swipeLevel(0.2f, -500f, 1000f), 0.0001f)
        assertEquals(1f, swipeLevel(0.9f, -1000f, 1000f), 0.0001f)
        assertEquals(0f, swipeLevel(0.1f, 400f, 1000f), 0.0001f)
        assertEquals(0.5f, swipeLevel(0.5f, 100f, 0f), 0.0001f)
    }

    @Test
    fun volumeStepsRoundTrip() {
        assertEquals(8, volumeStep(0.5f, 15))
        assertEquals(15, volumeStep(1.4f, 15))
        assertEquals(0.4f, volumeLevel(6, 15), 0.0001f)
        assertEquals(0f, volumeLevel(3, 0), 0.0001f)
    }

    @Test
    fun barMathMapsFractionsAndPositions() {
        assertEquals(30_000L, barPosition(0.25f, 120_000))
        assertEquals(0L, barPosition(0.5f, 0))
        assertEquals(0.5f, barFraction(60_000, 120_000), 0.0001f)
        assertEquals(1f, barFraction(200_000, 120_000), 0.0001f)
        assertEquals(0f, barFraction(5_000, 0), 0.0001f)
    }

    @Test
    fun fittedSizeLetterboxesAndPillarboxes() {
        // A 16:9 picture on a portrait phone: full width, a band.
        val (w, h) = fittedSize(400f, 800f, 16f / 9f)
        assertEquals(400f, w, 0.01f)
        assertEquals(225f, h, 0.01f)
        // A 4:3 picture on a landscape phone: full height, pillarboxed.
        val (w2, h2) = fittedSize(800f, 360f, 4f / 3f)
        assertEquals(480f, w2, 0.01f)
        assertEquals(360f, h2, 0.01f)
        assertEquals(800f to 360f, fittedSize(800f, 360f, 0f))
    }

    @Test
    fun pipAspectStaysInsideTheSystemLimits() {
        assertEquals(1778 to 1000, pipAspect(16f / 9f))
        assertEquals(2390 to 1000, pipAspect(3f))
        assertEquals(420 to 1000, pipAspect(0.2f))
        assertEquals(1778 to 1000, pipAspect(0f))
        val (w, h) = pipAspect(0.1f)
        assertTrue(w.toFloat() / h >= 1 / 2.39f)
    }
}
