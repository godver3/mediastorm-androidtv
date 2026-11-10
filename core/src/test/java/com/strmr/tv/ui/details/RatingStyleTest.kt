package com.strmr.tv.ui.details

import com.strmr.tv.api.Rating
import com.strmr.tv.api.Title
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** The details ratings line: decoding from the bundle, Expo's order and score formats. */
class RatingStyleTest {
    @Test fun decodesFromTitle() {
        val json = """{"id": "tmdb:movie:603", "name": "The Matrix", "mediaType": "movie",
            "ratings": [{"source": "imdb", "value": 8.7, "max": 10}, {"source": "tomatoes", "value": 83, "max": 100}]}"""
        val title = Json { ignoreUnknownKeys = true }.decodeFromString<Title>(json)
        assertEquals(listOf("imdb", "tomatoes"), title.ratings?.map { it.source })
    }

    @Test fun expoOrderWithUnknownSourcesLast() {
        val ratings = listOf("letterboxd", "custom", "tomatoes", "imdb").map { Rating(it, 1.0, 10.0) }
        assertEquals(listOf("imdb", "tomatoes", "letterboxd", "custom"), RatingStyle.sorted(ratings).map { it.source })
    }

    @Test fun formats() {
        assertEquals("8.7", RatingStyle.of(Rating("imdb", 8.66, 10.0)).text)
        assertEquals("3.5", RatingStyle.of(Rating("letterboxd", 3.5, 5.0)).text)
        assertEquals("73%", RatingStyle.of(Rating("metacritic", 72.6, 100.0)).text)
        assertEquals("6.3", RatingStyle.of(Rating("other", 6.26, 10.0)).text)
        assertEquals("64%", RatingStyle.of(Rating("other", 64.0, 100.0)).text)
    }

    @Test fun rottenTomatoesFreshAtSixty() {
        assertEquals("rt_critics.png", RatingStyle.of(Rating("tomatoes", 60.0, 100.0)).icon)
        assertEquals("rt_rotten.png", RatingStyle.of(Rating("tomatoes", 59.0, 100.0)).icon)
        assertEquals("rt_critics.png", RatingStyle.of(Rating("tomatoes", 7.0, 10.0)).icon)
    }
}
