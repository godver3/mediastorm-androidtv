package com.strmr.tv.home

import com.strmr.tv.api.Title
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowsePageSizeTest {
    private fun page(count: Int, total: Int) =
        BrowsePage(List(count) { BrowseEntry.TitleEntry(Title(id = "t$it", name = "Title $it")) }, total)

    @Test fun shortPageMidListKeepsTheTotal() {
        // A decade list: 60 asked for, the server's TMDB discover cap returns 50 of 500.
        assertEquals(500, page(50, 500).listSize(fetched = 50))
    }

    @Test fun emptyPageEndsTheList() {
        assertEquals(180, page(0, 500).listSize(fetched = 180))
    }

    @Test fun totalBelowWhatWasReadEndsAtWhatWasRead() {
        assertEquals(47, page(47, 40).listSize(fetched = 47))
    }
}
