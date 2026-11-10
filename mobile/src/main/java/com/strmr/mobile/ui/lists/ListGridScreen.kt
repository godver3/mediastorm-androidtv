package com.strmr.mobile.ui.lists

import androidx.compose.runtime.Composable
import com.strmr.tv.home.BrowseList

/** A whole list as a poster grid: what a Lists tile, a Home row's title or an explore card opens. */
@Composable
fun ListGridScreen(list: BrowseList, query: Map<String, String>) {
    BrowseGridPage(list, query, showBack = true)
}
