package com.strmr.mobile.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.home.BrowseList

/** Placeholder until this screen is built. */
@Composable
fun ListGridScreen(list: BrowseList, query: Map<String, String>) {
    PageScaffold("List", showBack = true) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("List", style = MaterialTheme.typography.titleMedium)
        }
    }
}
