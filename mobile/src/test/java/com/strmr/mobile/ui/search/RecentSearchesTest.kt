package com.strmr.mobile.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {
    @Test fun newestFirstWithoutCaseDuplicates() {
        assertEquals(listOf("Dune", "alien"), RecentSearches.adding(listOf("alien", "dune"), " Dune "))
    }

    @Test fun blankIsIgnored() {
        assertEquals(listOf("a"), RecentSearches.adding(listOf("a"), "   "))
    }

    @Test fun keepsAtMostTheLimit() {
        val full = (1..RecentSearches.LIMIT).map { "q$it" }
        val next = RecentSearches.adding(full, "new")
        assertEquals(RecentSearches.LIMIT, next.size)
        assertEquals("new", next.first())
        assertEquals("q${RecentSearches.LIMIT - 1}", next.last())
    }
}
