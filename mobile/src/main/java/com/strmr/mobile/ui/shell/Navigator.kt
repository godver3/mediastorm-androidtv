package com.strmr.mobile.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.BrowseList

/**
 * Each section keeps its own back stack, as a phone's tabs do: switching tabs keeps where you
 * were, re-selecting the current tab returns to its root. Main thread only.
 */
class Navigator(start: Section = Section.HOME) {
    var section by mutableStateOf(start)
        private set
    private val stacks = mutableMapOf<Section, SnapshotStateList<Route>>()
    /** Bumped when the current tab is selected again at its root (scroll to top). */
    var reselected by mutableStateOf(0)
        private set
    /**
     * Set when the Search tab is pressed again: the search page takes it (focusing its field,
     * so the keyboard comes up) and clears it. A flag, not a counter, because a press from a
     * page pushed over Search also returns to the root, whose field composes fresh.
     */
    var searchFocusPending by mutableStateOf(false)

    fun stack(section: Section): SnapshotStateList<Route> = stacks.getOrPut(section) { mutableStateListOf() }

    val current: SnapshotStateList<Route> get() = stack(section)

    fun select(target: Section) {
        if (target == section) {
            if (target == Section.SEARCH) searchFocusPending = true
            val s = current
            if (s.isEmpty()) reselected++ else s.clear()
        } else {
            section = target
        }
    }

    fun push(route: Route) {
        // The same page twice in a row (a double tap) opens once.
        if (current.lastOrNull() != route) current.add(route)
    }

    fun pop(): Boolean {
        val s = current
        if (s.isEmpty()) return false
        s.removeAt(s.lastIndex)
        return true
    }

    fun openDetails(target: DetailsTarget, autoplay: Boolean = false) = push(Route.Details(target, autoplay))

    fun openList(list: BrowseList, query: Map<String, String>? = null) = push(Route.Grid(list, query.orEmpty()))
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator") }
