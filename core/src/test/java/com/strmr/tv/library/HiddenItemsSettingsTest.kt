package com.strmr.tv.library

import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.LossyList
import com.strmr.tv.library.HiddenItemsSettings.Section
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HiddenItemsSettingsTest {
    private fun decode(raw: String) = LossyList.decode(ApiJson, serializer<HiddenItem>(), raw)

    @Test fun decodesTheContinueWatchingHiddenList() {
        // GET /history/continue/hidden: the profile hidden-items shape, with hiddenAt and optional name/art.
        val items = decode(
            """
            [
              {"id":"tmdb:tv:68689","mediaType":"series","name":"Show","posterUrl":"https://image.tmdb.org/p.jpg",
               "externalIds":{"tmdb":"68689","tvdb":"328724"},"hiddenAt":"2026-11-19T10:00:00Z"},
              {"id":"tmdb:movie:550","mediaType":"movie","year":1999,"hiddenAt":"2026-11-18T10:00:00Z"}
            ]
            """.trimIndent(),
        )
        assertEquals(listOf("tmdb:tv:68689", "tmdb:movie:550"), items.map { it.id })
        assertEquals("Show", items[0].displayName)
        assertEquals("Unknown movie", items[1].displayName)
        assertEquals(1999, items[1].year)
        assertEquals(emptyList<HiddenItem>(), decode("[]"))
    }

    @Test fun targetCarriesKindAndProviderIds() {
        val target = HiddenItem("tmdb:tv:5", "series", "A Show", 2020, externalIds = mapOf("tmdb" to "5", "imdb" to "")).toTarget()
        assertEquals("tmdb:tv:5", target.titleId)
        assertEquals(false, target.isMovie)
        assertEquals("5", target.tmdbId)
        assertNull(target.imdbId)
        assertEquals(2020, target.year)
    }

    @Test fun rowKeysAreDistinctPerSectionAndKind() {
        val item = HiddenItem("tmdb:movie:1", "movie")
        assertEquals("cw:movie:tmdb:movie:1", HiddenItemsSettings.key(Section.ContinueWatching, item))
        assertEquals("hp:movie:tmdb:movie:1", HiddenItemsSettings.key(Section.Profile, item))
        assertEquals("hp:status", HiddenItemsSettings.statusKey(Section.Profile))
    }

    @Test fun focusMovesToTheNextRowElseTheOneAboveElseNowhere() {
        assertEquals(1, HiddenItemsSettings.focusIndexAfterRemoval(removedIndex = 1, remaining = 3))
        assertEquals(1, HiddenItemsSettings.focusIndexAfterRemoval(removedIndex = 2, remaining = 2))
        assertEquals(0, HiddenItemsSettings.focusIndexAfterRemoval(removedIndex = 0, remaining = 1))
        assertNull(HiddenItemsSettings.focusIndexAfterRemoval(removedIndex = 0, remaining = 0))
    }
}
