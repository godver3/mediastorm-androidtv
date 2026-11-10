package com.strmr.tv.live

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideLayoutTest {
    private fun ms(t: String) = Instant.parse("2026-11-02T${t}:00Z").toEpochMilli()
    private fun program(start: String, stop: String) =
        EpgProgram(title = "$start", start = "2026-11-02T${start}:00Z", stop = "2026-11-02T${stop}:00Z")

    @Test fun fillsGapsAndCutsAtTheWindow() {
        val cells = GuideLayout.cells(listOf(program("20:00", "20:30"), program("21:00", "22:30")), ms("20:00"), ms("22:00"))
        assertEquals(3, cells.size)
        assertNull(cells[1].program)
        assertEquals(ms("20:30"), cells[1].start)
        assertEquals(ms("22:00"), cells[2].end)
    }

    @Test fun shortGapsFoldIntoTheNextProgramme() {
        val cells = GuideLayout.cells(listOf(program("20:00", "20:28"), program("20:30", "21:00")), ms("20:00"), ms("21:00"))
        assertEquals(2, cells.size)
        assertEquals(ms("20:28"), cells[1].start)
    }

    @Test fun anEmptyRowIsOneGap() {
        val cells = GuideLayout.cells(emptyList(), ms("20:00"), ms("21:00"))
        assertEquals(1, cells.size)
        assertNull(cells[0].program)
    }
}
