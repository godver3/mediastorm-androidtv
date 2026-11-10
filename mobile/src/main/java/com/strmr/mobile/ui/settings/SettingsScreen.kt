package com.strmr.mobile.ui.settings

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
fun SettingsScreen(asPage: Boolean) {
    PageScaffold("Settings", showBack = asPage) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
        }
    }
}
