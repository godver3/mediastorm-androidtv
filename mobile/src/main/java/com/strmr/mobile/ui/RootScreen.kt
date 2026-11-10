package com.strmr.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strmr.mobile.ui.onboarding.LoginScreen
import com.strmr.mobile.ui.onboarding.PinSheet
import com.strmr.mobile.ui.onboarding.ProfilePickerScreen
import com.strmr.mobile.ui.onboarding.ServerScreen
import com.strmr.mobile.ui.onboarding.StartupUnavailable
import com.strmr.mobile.ui.playback.PlaybackOverlays
import com.strmr.mobile.ui.shell.MainScaffold
import com.strmr.mobile.ui.shell.ToastHost
import com.strmr.tv.app.AppSession

/** Startup phase → screen; a locked-profile PIN prompt over whatever is showing. */
@Composable
fun RootScreen() {
    val phase by AppSession.phase.collectAsState()
    val profile by AppSession.profile.collectAsState()
    val challenge by AppSession.pinChallenge.collectAsState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (val p = phase) {
                AppSession.Phase.Starting -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                AppSession.Phase.Server -> ServerScreen()
                AppSession.Phase.Login -> LoginScreen()
                AppSession.Phase.Profiles -> ProfilePickerScreen()
                // Rebuilt per profile so every screen reloads its data.
                AppSession.Phase.Ready -> key(profile?.id) { MainScaffold() }
                is AppSession.Phase.Unavailable -> StartupUnavailable(p.message)
            }
            PlaybackOverlays()
            ToastHost()
            challenge?.let { c ->
                PinSheet(
                    profile = c.profile,
                    verify = { AppSession.verifyChallenge(it) },
                    onCancel = { AppSession.cancelChallenge() },
                )
            }
        }
    }
}
