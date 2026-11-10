package com.strmr.mobile.ui.sports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strmr.mobile.ui.shell.PageScaffold


/** Placeholder until this screen is built. */
@Composable
fun SportsRaceScreen(weekendId: String, sessionId: String?) {
    PageScaffold("Race", showBack = true) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Race", style = MaterialTheme.typography.titleMedium)
        }
    }
}
