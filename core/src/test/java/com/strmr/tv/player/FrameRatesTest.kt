package com.strmr.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.roundToLong

class FrameRatesTest {
    private fun mode(id: Int, hz: Float, w: Int = 3840, h: Int = 2160) = FrameRates.Mode(id, w, h, hz)

    @Test fun parsesProbeRates() {
        assertEquals(23.976f, FrameRates.parse("24000/1001")!!, 0.001f)
        assertEquals(25f, FrameRates.parse("25/1")!!, 0.001f)
        assertEquals(29.97f, FrameRates.parse("29.97")!!, 0.001f)
        assertNull(FrameRates.parse("0/0"))
        assertNull(FrameRates.parse(""))
        assertNull(FrameRates.parse(null))
    }

    @Test fun prefersExactRateOverNearOne() {
        val modes = listOf(mode(1, 60f), mode(2, 24f), mode(3, 23.976f), mode(4, 59.94f))
        assertEquals(3, FrameRates.bestMode(23.976f, modes[0], modes)?.id)
        assertEquals(2, FrameRates.bestMode(24f, modes[0], modes)?.id)
    }

    @Test fun takesExactMultipleBeforeNearRate() {
        val modes = listOf(mode(1, 60f), mode(2, 24f), mode(3, 47.952f), mode(4, 119.88f))
        assertEquals(3, FrameRates.bestMode(23.976f, modes[0], modes)?.id)
    }

    @Test fun fallsBackToNearRate() {
        val modes = listOf(mode(1, 60f), mode(2, 24f))
        assertEquals(2, FrameRates.bestMode(23.976f, modes[0], modes)?.id)
    }

    @Test fun staysAtCurrentResolutionAndGivesUpWithoutFit() {
        val modes = listOf(mode(1, 60f), mode(2, 50f, 1920, 1080), mode(3, 59.94f))
        assertNull(FrameRates.bestMode(25f, modes[0], modes))
        assertEquals(3, FrameRates.bestMode(29.97f, modes[0], modes)?.id)
    }

    /** Feeds [fps] video with millisecond timestamps (as Matroska stores them), dropping every [dropEvery]th frame. */
    private fun measure(fps: Double, dropEvery: Int = 0, start: Long = 1_000_000): Float? {
        val cadence = FrameCadence()
        for (i in 0 until 1000) {
            if (dropEvery > 0 && i % dropEvery == dropEvery - 1) continue
            val ms = ((start / 1000.0) + i * 1000.0 / fps).roundToLong()
            cadence.feed(ms * 1000)?.let { return it }
        }
        return null
    }

    @Test fun cadenceTellsFilmRatesApart() {
        assertEquals(23.976f, measure(24000.0 / 1001)!!, 0.0001f)
        assertEquals(24f, measure(24.0)!!, 0.0001f)
        assertEquals(25f, measure(25.0)!!, 0.0001f)
        assertEquals(59.94f, measure(60000.0 / 1001)!!, 0.0001f)
    }

    @Test fun cadenceCountsDroppedFrames() {
        assertEquals(23.976f, measure(24000.0 / 1001, dropEvery = 17)!!, 0.0001f)
    }

    @Test fun cadenceRestartsAfterSeek() {
        val cadence = FrameCadence()
        var t = 0L
        repeat(50) { cadence.feed(t); t += 41_708 }
        t += 600_000_000
        var result: Float? = null
        repeat(200) { if (result == null) result = cadence.feed(t); t += 41_708 }
        assertEquals(23.976f, result!!, 0.0001f)
    }
}
