package com.strmr.tv.ui.lists

import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.Profile
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.BrowsePage
import com.strmr.tv.home.CustomList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PersonalAreaTest {
    @After fun cleanup() { BrowsePreviews.reset() }

    @Test fun missingAndChildProfilesCannotOpenTheNewAggregate() {
        assertFalse(PersonalArea.available(null))
        for (mode in listOf(null, "rating", "content_list")) {
            assertFalse(PersonalArea.available(Profile("child", "Synthetic child", isKidsProfile = true, kidsMode = mode)))
        }
        assertTrue(PersonalArea.available(Profile("adult", "Synthetic adult")))
        assertTrue(PersonalArea.available(Profile("adult", "Synthetic adult", isKidsProfile = false)))
    }

    @Test fun stableChoicesDoNotMoveWhenLoadingFinishesOrListsEmpty() {
        val loading = PersonalArea.tiles(null, null, 0)
        val empty = PersonalArea.tiles(emptyList(), 0, 0)
        val loaded = PersonalArea.tiles(listOf(ContinueWatchingItem("tmdb:movie:1", "Synthetic movie")), 5, 3)
        val expected = listOf(BrowseList.ContinueWatching, BrowseList.Watchlist, PersonalArea.loved, BrowseList.Personalized)
        for (tiles in listOf(loading, empty, loaded)) assertEquals(expected, tiles.map { it.list })
    }

    @Test fun unknownCountsAreNotPresentedAsEmptySuccessfulLists() {
        val loading = PersonalArea.tiles(null, null, 0)
        assertNull(loading[0].count); assertNull(loading[1].count); assertNull(loading[2].count)
        val empty = PersonalArea.tiles(emptyList(), 0, 0)
        assertEquals(0, empty[0].count); assertEquals(0, empty[1].count); assertNull(empty[2].count)
    }

    @Test fun myListUsesTheExistingWatchlistIdentityAndTransport() {
        val saved = PersonalArea.tiles(null, null, 0)[1]
        assertSame(BrowseList.Watchlist, saved.list)
        assertEquals("My List", saved.title)
        assertEquals(mapOf("source" to "watchlist"), saved.list.query)
    }

    @Test fun lovedTitlesUseTheExistingPreferenceSourceWithoutExternalIdentity() {
        assertEquals(mapOf("source" to "loved"), PersonalArea.loved.query)
        assertFalse(PersonalArea.loved.itemShaped)
        assertFalse(PersonalArea.loved.titleShaped)
    }

    @Test fun preferenceRevisionRefreshesOnlyLovedArtworkWithoutChangingCountsOrKeys() {
        val before = PersonalArea.tiles(emptyList(), 4, 1)
        val after = PersonalArea.tiles(emptyList(), 4, Long.MAX_VALUE)
        for (index in before.indices) {
            assertEquals(before[index].list, after[index].list)
            assertEquals(before[index].count, after[index].count)
            if (before[index].list == PersonalArea.loved) assertNotEquals(before[index].previewRevision, after[index].previewRevision)
            else assertEquals(before[index], after[index])
        }
    }

    @Test fun recommendationGenerationIsNotRequestedToDecorateThePersonalEntry() {
        val recommendations = PersonalArea.tiles(null, null, 0).single { it.list == BrowseList.Personalized }
        assertEquals(TileArt(), recommendations.art)
        assertNull(recommendations.count)
    }

    @Test fun previewUsesOnlyTheFirstFourExistingContinueWatchingImages() {
        val watching = (0..7).map { ContinueWatchingItem("tmdb:movie:$it", "Synthetic $it", posterUrl = "poster-$it", textPosterUrl = "text-$it", backdropUrl = "back-$it") }
        val preview = PersonalArea.tiles(watching, null, 0).first()
        assertEquals(8, preview.count)
        assertEquals((0..3).map { "text-$it" }, preview.art!!.posters)
        assertEquals((0..3).map { "back-$it" }, preview.art!!.backdrops)
        assertEquals(8, watching.size)
    }

    @Test fun reservedSetAsideIsNotDuplicatedAsAnOwnUserList() {
        val lists = listOf(CustomList("a", "Synthetic one", 0), CustomList("parked", " set aside ", 2), CustomList("b", "Synthetic two", 4))
        val tiles = PersonalArea.ownLists(lists)
        assertEquals(listOf("a", "b"), tiles.map { (it.list as BrowseList.Custom).id })
        assertEquals(listOf(0, 4), tiles.map { it.count })
        assertEquals(3, lists.size)
    }

    @Test fun ownListsKeepServerOrderAndStableIdentityWhenNamesOrCountsChange() {
        val lists = listOf(CustomList("b", "Z", 2), CustomList("a", "A", 1), CustomList("b", "Duplicate", 7))
        assertEquals(listOf("b", "a"), PersonalArea.ownLists(lists).map { (it.list as BrowseList.Custom).id })
        assertTrue(PersonalArea.ownLists(null).isEmpty())
        assertTrue(PersonalArea.ownLists(emptyList()).isEmpty())
        val renamed = PersonalArea.ownLists(listOf(CustomList("b", "Renamed", 8))).single()
        assertEquals(PersonalArea.ownLists(lists).first().key, renamed.key)
    }

    @Test fun aPreferenceRevisionInvalidatesThePreviewEvenWhenItsCountIsUnchanged() = runTest {
        val cache = ProfilePreviewCache(); var reads = 0
        cache.art("loved", null, 1) { reads++; TileArt(posters = listOf("old")) }
        val after = cache.art("loved", null, 2) { reads++; TileArt(posters = listOf("new")) }
        assertEquals(listOf("new"), after.posters); assertEquals(2, reads)
    }

    @Test fun actualPreviewEntryUsesThePreferenceRevisionWithoutAnotherListDataset() = runTest {
        var reads = 0
        BrowsePreviews.art(PersonalArea.loved, null, 1) { reads++; BrowsePage(emptyList(), 0) }
        BrowsePreviews.art(PersonalArea.loved, null, 1) { reads++; BrowsePage(emptyList(), 0) }
        BrowsePreviews.art(PersonalArea.loved, null, 2) { reads++; BrowsePage(emptyList(), 0) }
        assertEquals(2, reads)
    }
}
