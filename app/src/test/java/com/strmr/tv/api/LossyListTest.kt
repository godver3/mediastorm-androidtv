package com.strmr.tv.api

import kotlinx.serialization.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LossyListTest {
    private val logged = mutableListOf<String>()
    private lateinit var previous: (String) -> Unit

    @Before fun collectLogs() {
        previous = LossyList.report
        LossyList.report = { logged += it }
    }

    @After fun restoreLogs() {
        LossyList.report = previous
    }

    private fun continueWatching(raw: String) = LossyList.decode(ApiJson, serializer<ContinueWatchingItem>(), raw)

    @Test fun nullBodyIsEmpty() {
        // Go's nil slice: what /history/continue answers for a profile with nothing to continue.
        assertEquals(emptyList<ContinueWatchingItem>(), continueWatching("null"))
        assertEquals(emptyList<ContinueWatchingItem>(), continueWatching("[]"))
        assertTrue(logged.isEmpty())
    }

    @Test fun itemMissingRequiredFieldIsSkippedAndLogged() {
        val items = continueWatching(
            """[{"seriesId":"tmdb:movie:1","seriesTitle":"One"},{"seriesId":"tmdb:movie:2"},{"seriesId":"tmdb:movie:3","seriesTitle":"Three"}]""",
        )
        assertEquals(listOf("tmdb:movie:1", "tmdb:movie:3"), items.map { it.seriesId })
        assertEquals(1, logged.size)
        assertTrue(logged[0], logged[0].startsWith("skipped ContinueWatchingItem #1: "))
        assertTrue(logged[0], "seriesTitle" in logged[0])
    }

    @Test fun partlyBadPageKeepsTheGoodItems() {
        val page = ApiJson.decodeFromString<DisplayListPage<ContinueWatchingItem>>(
            """{"items":[{"seriesId":"a","seriesTitle":"A"},"junk",{"seriesId":"b","seriesTitle":42.5,"year":"x"},{"seriesId":"c","seriesTitle":"C"}],"total":4}""",
        )
        assertEquals(listOf("a", "c"), page.items.map { it.seriesId })
        assertEquals(4, page.total)
        assertEquals(listOf("#1", "#2"), logged.map { it.substringAfter("ContinueWatchingItem ").substringBefore(':') })
    }

    @Test fun nullOrMissingPageItemsAreEmpty() {
        assertEquals(0, ApiJson.decodeFromString<DisplayListPage<Title>>("""{"items":null,"total":0}""").items.size)
        assertEquals(0, ApiJson.decodeFromString<DisplayListPage<Title>>("""{"total":0}""").items.size)
        assertTrue(logged.isEmpty())
    }
}
