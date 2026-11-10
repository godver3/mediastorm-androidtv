package com.strmr.mobile.ui.onboarding

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.strmr.tv.app.AppSession

/** A stored session couldn't be checked because the server didn't answer. */
@Composable
fun StartupUnavailable(message: String) {
    val server by AppSession.serverUrl.collectAsState()
    OnboardingScaffold(title = "Couldn't Connect to ${server?.host ?: "the Server"}", subtitle = message) {
        Button(onClick = { AppSession.retryStartup() }) { Text("Try Again") }
        OutlinedButton(onClick = { AppSession.changeServer() }) { Text("Change Server") }
    }
}
