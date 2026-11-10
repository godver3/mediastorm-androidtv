package com.strmr.tv.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceMergeTest {
    @Test fun alternatesWhileBothListsHaveTitles() {
        val window = interleave(listOf(3, 3), 0, 4)
        assertEquals(listOf(0, 1, 0, 1), window.order)
        assertEquals(listOf(0 to 2, 0 to 2), window.ranges)
    }

    @Test fun laterPageStartsEachListWhereTheLastLeftOff() {
        val window = interleave(listOf(3, 3), 4, 4)
        assertEquals(listOf(0, 1), window.order)
        assertEquals(listOf(2 to 1, 2 to 1), window.ranges)
    }

    @Test fun shorterListDropsOutOfTheTurn() {
        // 0 1 0 1 0 0 0 — the second list has two titles.
        val window = interleave(listOf(5, 2), 3, 4)
        assertEquals(listOf(1, 0, 0, 0), window.order)
        assertEquals(listOf(2 to 3, 1 to 1), window.ranges)
    }

    @Test fun pastTheEndIsEmpty() {
        val window = interleave(listOf(1, 1), 2, 5)
        assertEquals(emptyList<Int>(), window.order)
        assertEquals(listOf(1 to 0, 1 to 0), window.ranges)
    }
}
