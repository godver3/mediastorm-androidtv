package com.strmr.tv.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** Card sizes for one kind of Home row: Default follows Card Size, a chosen size sizes that kind alone. */
class StyleCardSizesTest {
    @Test fun unsetStylesFollowCardSize() {
        for (style in ShelfStyle.entries) assertEquals(0.8, StyleCardSizes.scale(emptyMap(), style, 0.8), 0.0)
    }

    @Test fun styleSizeAppliesToThatStyleAlone() {
        val sizes = mapOf(ShelfStyle.LANDSCAPE to CardSize.SMALL)
        assertEquals(CardSize.SMALL.scale, StyleCardSizes.scale(sizes, ShelfStyle.LANDSCAPE, 0.8), 0.0)
        assertEquals(0.8, StyleCardSizes.scale(sizes, ShelfStyle.POSTER, 0.8), 0.0)
        assertEquals(0.8, StyleCardSizes.scale(sizes, ShelfStyle.EXPANDING_POSTER, 0.8), 0.0)
    }

    @Test fun savedSizesRoundTripAndIgnoreUnknownEntries() {
        val sizes = mapOf(ShelfStyle.POSTER to CardSize.EXTRA_LARGE, ShelfStyle.EXPANDING_POSTER to CardSize.SMALL)
        assertEquals(sizes, StyleCardSizes.decode(StyleCardSizes.encode(sizes)))
        assertEquals(mapOf(ShelfStyle.LANDSCAPE to CardSize.MEDIUM), StyleCardSizes.decode(setOf("LANDSCAPE=MEDIUM", "BOGUS=SMALL", "POSTER=HUGE")))
    }
}
