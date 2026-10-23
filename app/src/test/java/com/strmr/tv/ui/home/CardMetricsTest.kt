package com.strmr.tv.ui.home

import com.strmr.tv.home.CardSize
import com.strmr.tv.home.CardText
import com.strmr.tv.home.ShelfStyle
import com.strmr.tv.ui.CardFocusScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home row heights: rows without captions keep the gap that clears a focused card's lift. */
class CardMetricsTest {
    @Test fun rowWithoutCaptionsClearsFocusedCard() {
        for (size in CardSize.entries) {
            val m = CardMetrics(ShelfStyle.POSTER, size.scale, CardText.NONE)
            val below = m.rowHeight.value - 23f - m.liftRoom.value - m.height.value
            val overhang = m.height.value * (CardFocusScale - 1f) / 2f
            assertTrue("${size.label}: $below below, $overhang overhang", below - overhang >= 25f)
        }
    }

    @Test fun captionsAddTheirTextOverTheGap() {
        val scale = CardSize.EXTRA_LARGE.scale
        val withText = CardMetrics(ShelfStyle.POSTER, scale, CardText.BELOW).rowHeight
        val without = CardMetrics(ShelfStyle.POSTER, scale, CardText.NONE).rowHeight
        assertEquals(21f, withText.value - without.value, 1e-3f)
    }
}
