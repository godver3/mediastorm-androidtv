package com.strmr.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsWindowTest {
    private val roll1 = listOf("Directed by", "Jane Smith", "Produced by")
    private val roll2 = listOf("Executive Producer", "John Doe", "Casting")
    private val roll3 = listOf("Director of Photography", "Ann Lee")

    @Test fun samplesOnlyInTheLastTenth() {
        val w = CreditsWindow()
        assertFalse(w.shouldSample(800.0, 1000.0))
        assertTrue(w.shouldSample(900.0, 1000.0))
        assertFalse(w.shouldSample(0.0, 0.0))
    }

    @Test fun coarseUntilTextThenFine() {
        val w = CreditsWindow()
        assertTrue(w.shouldSample(900.0, 1000.0))
        assertFalse(w.shouldSample(903.0, 1000.0))
        assertTrue(w.shouldSample(905.0, 1000.0))
        w.onText(905.0, roll1)
        assertTrue(w.shouldSample(906.0, 1000.0))
    }

    @Test fun rollingCreditsDetectAtTheFirstSample() {
        val w = CreditsWindow()
        assertNull(w.onText(940.0, roll1))
        assertNull(w.onText(941.0, roll2))
        assertEquals(940.0, w.onText(942.0, roll3)!!, 0.0)
        assertFalse(w.shouldSample(950.0, 1000.0))
    }

    @Test fun aStillTitleCardIsNotCredits() {
        val w = CreditsWindow()
        assertNull(w.onText(940.0, roll1))
        assertNull(w.onText(941.0, roll1))
        assertNull(w.onText(942.0, roll1))
        assertNull(w.detectedAtSec)
    }

    @Test fun aFrameWithoutCreditTextStartsOver() {
        val w = CreditsWindow()
        w.onText(940.0, roll1)
        w.onText(941.0, roll2)
        assertNull(w.onText(942.0, listOf("12:45")))
        assertNull(w.onText(943.0, roll3))
    }

    @Test fun creditTextFilter() {
        assertTrue(CreditsWindow.looksLikeCredit("jane smith"))
        assertFalse(CreditsWindow.looksLikeCredit("x"))
        assertFalse(CreditsWindow.looksLikeCredit("2024 1080 5 1"))
        assertEquals("directed by j smith", CreditsWindow.normalize("  Directed by: J. Smith "))
    }
}
