package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.app.AppSession
import com.strmr.tv.ui.onboarding.LoginScreen
import com.strmr.tv.ui.onboarding.OnboardingLayout
import com.strmr.tv.ui.onboarding.ProfileLockPrompt
import com.strmr.tv.ui.onboarding.ProfilePickerScreen
import com.strmr.tv.ui.onboarding.ServerConnectScreen
import kotlinx.coroutines.launch

/** Startup phase → screen; a locked-profile PIN prompt over whatever is showing. */
@Composable
fun RootScreen() {
    val phase by AppSession.phase.collectAsState()
    val profile by AppSession.profile.collectAsState()
    val challenge by AppSession.pinChallenge.collectAsState()

    Box(Modifier.fillMaxSize().background(if (phase == AppSession.Phase.Ready) androidx.compose.ui.graphics.Color.Black else BrandBackground)) {
        when (val p = phase) {
            AppSession.Phase.Starting -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
            AppSession.Phase.Server -> ServerConnectScreen()
            AppSession.Phase.Login -> LoginScreen()
            AppSession.Phase.Profiles -> ProfilePickerScreen()
            // Rebuilt per profile so every page reloads its data.
            AppSession.Phase.Ready -> key(profile?.id) { PageStack { MainShell() } }
            is AppSession.Phase.Unavailable -> StartupUnavailable(p.message)
        }
        PlaybackOverlays()
        challenge?.let { ProfileLockPrompt(it) }
    }
}

/** A stored session couldn't be checked because the server didn't answer. */
@Composable
private fun StartupUnavailable(message: String) {
    val server by AppSession.serverUrl.collectAsState()
    val scope = rememberCoroutineScope()
    OnboardingLayout(title = "Couldn't Connect to ${server?.host ?: "the Server"}", subtitle = message) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Button(onClick = { scope.launch { AppSession.resumeSession() } }) { Text("Try Again") }
            OutlinedButton(onClick = { AppSession.changeServer() }) { Text("Change Server") }
        }
    }
}
