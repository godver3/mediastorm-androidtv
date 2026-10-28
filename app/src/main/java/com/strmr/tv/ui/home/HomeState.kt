package com.strmr.tv.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.home.HomeStore
import kotlinx.coroutines.CoroutineScope

/**
 * Home's stores, one per view, and where focus was on each view's page. Held by the shell, as
 * tvOS keeps its Home stores, so leaving Home for another tab and coming back neither reloads
 * its rows nor forgets the card left: Home's page leaves composition while another tab is up.
 */
class HomeState(private val scope: CoroutineScope) {
    private val stores = mutableMapOf<String, HomeStore>()
    private val pages = mutableMapOf<String, HomeFocusMemory>()

    fun store(view: String): HomeStore = stores.getOrPut(view) { HomeStore(scope, view) }

    /** A view's page memory, fresh for each layout the page is built for (its key). */
    internal fun page(key: String): HomeFocusMemory = pages.getOrPut(key) { HomeFocusMemory() }
}

/** Where focus was on one Home page: the row and card, each row's scroll, and the hero. */
internal class HomeFocusMemory {
    val column = LazyListState()
    val shelves = mutableMapOf<String, ShelfMemory>()
    /** Each row's last focused card's info. */
    val heroes = mutableMapOf<String, HeroContent>()
    var focusedId by mutableStateOf<String?>(null)
    var focusedIndex by mutableIntStateOf(0)
    var hero by mutableStateOf<HeroContent?>(null)
    /** The page has taken its first focus (or left it on the tabs) at launch. */
    var focusedOnce by mutableStateOf(false)
}
