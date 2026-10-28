package com.strmr.tv.ui.home

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Animate Expansion off: an expanding row's card opens and folds at once, with no glide. */
class RowFocusTest {
    private fun row() = RowFocus(LazyListState()).apply {
        count = 5
        expands = true
        animates = false
    }

    @Test fun focusOpensTheCardAtOnce() = runBlocking {
        val focus = row()
        focus.focus(1)
        focus.focus(3)
        assertEquals(3f, focus.at.value)
        assertEquals(1f, focus.open(3))
        assertEquals(0f, focus.open(1))
    }

    @Test fun leavingFoldsTheCardAtOnce() = runBlocking {
        val focus = row()
        focus.focus(2)
        focus.leave()
        assertEquals(0f, focus.open(2))
    }
}
