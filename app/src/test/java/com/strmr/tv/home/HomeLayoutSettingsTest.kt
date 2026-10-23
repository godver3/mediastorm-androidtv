package com.strmr.tv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two Home switches: migration from the old Home Style, sub-settings, card sizes. */
class HomeLayoutSettingsTest {
    @Test fun carouselStyleBecomesTopCarouselWithoutInfo() {
        assertEquals(true to false, HomeStyleMigration.migrate("CAROUSEL"))
    }

    @Test fun classicOrUnknownStyleBecomesDefaults() {
        assertEquals(false to true, HomeStyleMigration.migrate("CLASSIC"))
        assertEquals(false to true, HomeStyleMigration.migrate(null))
        assertEquals(false to true, HomeStyleMigration.migrate("bogus"))
    }

    @Test fun defaultsShowClassicSettingsOnly() {
        val o = HomeLayoutOptions(topCarousel = false, focusedInfo = true)
        assertFalse(o.showsCarouselRow)
        assertTrue(o.showsHeroSize)
        assertTrue(o.showsTextSize)
        assertEquals("Hero Text Size", o.textSizeLabel)
    }

    @Test fun carouselOnlyShowsRowAndCarouselText() {
        val o = HomeLayoutOptions(topCarousel = true, focusedInfo = false)
        assertTrue(o.showsCarouselRow)
        assertFalse(o.showsHeroSize)
        assertTrue(o.showsTextSize)
        assertEquals("Carousel Text Size", o.textSizeLabel)
    }

    @Test fun bothShowEverything() {
        val o = HomeLayoutOptions(topCarousel = true, focusedInfo = true)
        assertTrue(o.showsCarouselRow && o.showsHeroSize && o.showsTextSize)
        assertEquals("Hero & Carousel Text Size", o.textSizeLabel)
    }

    @Test fun neitherHidesAllSubSettings() {
        val o = HomeLayoutOptions(topCarousel = false, focusedInfo = false)
        assertFalse(o.showsCarouselRow || o.showsHeroSize || o.showsTextSize)
    }

    @Test fun extraLargeIsAQuarterOverLarge() {
        assertEquals(listOf("Small", "Medium", "Large", "Extra Large"), CardSize.entries.map { it.label })
        assertEquals(1.25, CardSize.EXTRA_LARGE.scale / CardSize.LARGE.scale, 1e-9)
        assertEquals(169, Math.round(135 * CardSize.EXTRA_LARGE.scale).toInt())
        // A profile's scale (0.5–1) never maps to Extra Large.
        assertEquals(CardSize.LARGE, CardSize.nearest(1.0))
    }
}
