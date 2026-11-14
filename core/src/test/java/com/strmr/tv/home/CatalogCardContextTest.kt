package com.strmr.tv.home

import com.strmr.tv.api.Title
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCardContextTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun serverChartContextSurvivesDecodingAndHomeMapping() {
        val title = json.decodeFromString<Title>(
            """{"id":"tmdb:tv:123","name":"Series","mediaType":"series","cardSubtitle":"Netflix #4 · DE · week 2026-11-08 · Season 2","forceTitleOverlay":true,"cardImage":{"url":"https://example.test/chart.jpg"}}""",
        )
        val card = HomeCard.from(title)
        assertEquals("Netflix #4 · DE · week 2026-11-08 · Season 2", card.subtitle)
        assertTrue(card.forceTitleOverlay)
        assertEquals("https://example.test/chart.jpg", card.artworkUrl)
        assertEquals("tmdb:tv:123", card.target?.titleId)
    }

    @Test fun olderServersRetainTheOrdinaryMetadataLine() {
        val title = json.decodeFromString<Title>("""{"id":"movie","name":"Movie","year":2024}""")
        val card = HomeCard.from(title)
        assertTrue(card.subtitle.contains("2024"))
        assertFalse(card.forceTitleOverlay)
    }

    @Test fun blankContextDoesNotHideTheOrdinaryMetadataLine() {
        val card = HomeCard.from(Title(id = "movie", name = "Movie", year = 2024, cardSubtitle = "  "))
        assertTrue(card.subtitle.contains("2024"))
    }
}
