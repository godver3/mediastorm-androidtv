package com.strmr.mobile.ui.calendar

import com.strmr.tv.calendar.CalendarDay
import com.strmr.tv.home.CalendarItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarLayoutTest {
    private fun day(key: String, count: Int) = CalendarDay(key, List(count) { CalendarItem(title = "t$it", airDate = key) })
    private val days = listOf(day("2026-11-09", 2), day("2026-11-10", 1), day("2026-11-12", 3))

    @Test fun headerIndexCountsEachDaysHeaderAndReleases() {
        assertEquals(0, CalendarLayout.headerIndex(days, "2026-11-09"))
        assertEquals(3, CalendarLayout.headerIndex(days, "2026-11-10"))
        assertEquals(5, CalendarLayout.headerIndex(days, "2026-11-12"))
        assertNull(CalendarLayout.headerIndex(days, "2026-11-11"))
    }

    @Test fun dayAtMapsRowsBackToTheirDay() {
        assertEquals("2026-11-09", CalendarLayout.dayAt(days, 2))
        assertEquals("2026-11-10", CalendarLayout.dayAt(days, 3))
        assertEquals("2026-11-10", CalendarLayout.dayAt(days, 4))
        assertEquals("2026-11-12", CalendarLayout.dayAt(days, 8))
        assertEquals("2026-11-12", CalendarLayout.dayAt(days, 99))
    }

    @Test fun sourceLabels() {
        assertEquals("Top", CalendarLayout.sourceLabel("top-trending"))
        assertNull(CalendarLayout.sourceLabel(null))
    }
}
