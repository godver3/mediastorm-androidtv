package com.strmr.mobile.ui.person

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.api.PersonRef

/** Placeholder until this screen is built. */
@Composable
fun PersonScreen(person: PersonRef) {
    PageScaffold("Person", showBack = true) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Person", style = MaterialTheme.typography.titleMedium)
        }
    }
}
