package com.strmr.tv.home

import com.strmr.tv.api.DetailsTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarouselPickTest {
    private fun card(id: String) = HomeCard(id, HomeLink.Details(DetailsTarget(id, id, true)), id, "")
    private fun shelf(id: String, vararg cards: String) = HomeSlot.Shelf(HomeShelf(id, id, cards.map(::card)))
    private fun actionShelf(id: String) =
        HomeSlot.Shelf(HomeShelf(id, id, listOf(HomeCard("a", HomeLink.Action(HomeAction.SURPRISE), "a", ""))))

    @Test fun chosenRowWins() {
        val slots = listOf(shelf("continue-watching", "x"), shelf("top-ten", "a"), shelf("trending-tv", "b"))
        assertEquals("trending-tv", CarouselPick.pick(slots, "trending-tv"))
    }

    @Test fun defaultsToTopTenThenTrending() {
        assertEquals("top-ten", CarouselPick.pick(listOf(shelf("continue-watching", "x"), shelf("top-ten", "a")), null))
        assertEquals("trending-movies", CarouselPick.pick(listOf(shelf("continue-watching", "x"), shelf("trending-movies", "a")), null))
        assertEquals("trending-movies", CarouselPick.pick(listOf(shelf("top-ten"), shelf("trending-movies", "a")), "gone"))
    }

    @Test fun fallsBackToFirstRowWithTitles() {
        val slots = listOf(actionShelf("watch-something"), HomeSlot.Failed("top-ten", "Top 10"), shelf("watchlist", "w"))
        assertEquals("watchlist", CarouselPick.pick(slots, null))
        assertNull(CarouselPick.pick(listOf(actionShelf("watch-something")), null))
    }

    @Test fun waitsForPreferredRowStillLoading() {
        val slots = listOf(shelf("continue-watching", "x"), HomeSlot.Loading("top-ten", "Top 10"))
        assertEquals("top-ten", CarouselPick.pick(slots, null))
    }

    @Test fun slidesAreCappedTitles() {
        val many = HomeShelf("top-ten", "Top 10", (1..15).map { card("t$it") }, cap = 20)
        assertEquals(CarouselPick.MAX, CarouselPick.slides(many).size)
    }
}
