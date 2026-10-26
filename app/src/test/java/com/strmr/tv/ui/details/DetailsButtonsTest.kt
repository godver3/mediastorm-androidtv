package com.strmr.tv.ui.details

import org.junit.Assert.assertEquals
import org.junit.Test

/** Customize Buttons: what's in More when nothing is stored, and reading back stored choices. */
class DetailsButtonsTest {
    @Test fun manageListsStartsInMore() {
        assertEquals(setOf(DetailsAction.LISTS), DetailsButtons.decode(null))
    }

    @Test fun storedChoicesReplaceTheDefaults() {
        assertEquals(emptySet<DetailsAction>(), DetailsButtons.decode(emptySet()))
        assertEquals(setOf(DetailsAction.TRAILER, DetailsAction.WATCHED), DetailsButtons.decode(setOf("TRAILER", "WATCHED")))
    }

    @Test fun unknownNamesAreDropped() {
        assertEquals(setOf(DetailsAction.VERSIONS), DetailsButtons.decode(setOf("VERSIONS", "GONE")))
    }
}
