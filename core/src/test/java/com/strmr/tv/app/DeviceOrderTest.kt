package com.strmr.tv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceOrderTest {
    @Test fun emptySavedFollowsServer() {
        assertEquals(listOf("a", "b", "c"), DeviceOrder.merge(emptyList(), listOf("a", "b", "c")))
    }

    @Test fun savedOrderWins() {
        assertEquals(listOf("c", "a", "b"), DeviceOrder.merge(listOf("c", "a", "b"), listOf("a", "b", "c")))
    }

    @Test fun goneIdsDrop() {
        assertEquals(listOf("c", "a"), DeviceOrder.merge(listOf("c", "x", "a"), listOf("a", "c")))
    }

    @Test fun newIdFollowsItsServerNeighbour() {
        // Server added "n" after "b"; the user had moved "b" to the front.
        assertEquals(listOf("b", "n", "c", "a"), DeviceOrder.merge(listOf("b", "c", "a"), listOf("a", "b", "n", "c")))
    }

    @Test fun newLeadingIdsGoFirstInServerOrder() {
        assertEquals(listOf("n1", "n2", "b", "a"), DeviceOrder.merge(listOf("b", "a"), listOf("n1", "n2", "a", "b")))
    }

    @Test fun consecutiveNewIdsStayTogether() {
        assertEquals(listOf("b", "a", "n1", "n2"), DeviceOrder.merge(listOf("b", "a"), listOf("a", "n1", "n2", "b")))
    }

    @Test fun duplicatesCollapse() {
        assertEquals(listOf("b", "a"), DeviceOrder.merge(listOf("b", "b", "a"), listOf("a", "b", "a")))
    }

    @Test fun applyReordersItems() {
        data class Item(val id: String)
        val items = listOf(Item("a"), Item("b"), Item("c"))
        assertEquals(listOf(Item("c"), Item("a"), Item("b")), DeviceOrder.apply(items, listOf("c", "a"), Item::id))
        assertEquals(items, DeviceOrder.apply(items, emptyList(), Item::id))
    }

    @Test fun saveKeepsUnlistedIdsAfterShown() {
        assertEquals(listOf("c", "a", "hidden"), DeviceOrder.save(listOf("a", "hidden", "c"), listOf("c", "a")))
    }

    @Test fun moveShiftsItem() {
        assertEquals(listOf("b", "c", "a"), DeviceOrder.move(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), DeviceOrder.move(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b"), DeviceOrder.move(listOf("a", "b"), 0, 5))
    }

    @Test fun encodeRoundTrips() {
        val order = listOf("top-ten", "page-abc", "view:movies")
        assertEquals(order, DeviceOrder.decode(DeviceOrder.encode(order)))
        assertEquals(emptyList<String>(), DeviceOrder.decode(null))
        assertEquals(emptyList<String>(), DeviceOrder.decode(""))
    }

    @Test fun menuOrderKeepsHomeFirstAndSettingsLast() {
        val menu = listOf("home", "movies", "live", "watchlist", "search", "settings")
        val saved = listOf("search", "settings", "home", "watchlist", "movies")
        // Home and Settings stay put whatever the saved order says; "live" (not saved) follows "movies".
        assertEquals(
            listOf("home", "search", "watchlist", "movies", "live", "settings"),
            DeviceOrder.applyPinned(menu, saved, { it }, first = { it == "home" }, last = { it == "settings" }),
        )
    }

    @Test fun menuOrderWithoutSavedOrderIsUnchanged() {
        val menu = listOf("home", "live", "search", "settings")
        assertEquals(menu, DeviceOrder.applyPinned(menu, emptyList(), { it }, first = { it == "home" }, last = { it == "settings" }))
    }
}
