package com.strmr.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LazyKeysTest {
    @Test fun keepsUniqueIdsAsTheyAre() {
        assertEquals(listOf("a", "b", "c"), uniqueKeys(listOf("a", "b", "c")) { it })
    }

    @Test fun suffixesOnlyRepeats() {
        assertEquals(listOf("", "a", "#2", "a#2", "#3"), uniqueKeys(listOf("", "a", "", "a", "")) { it })
    }
}
