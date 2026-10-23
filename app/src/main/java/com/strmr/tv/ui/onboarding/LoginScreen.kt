package com.strmr.tv.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiError
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.DebugPrefill
import com.strmr.tv.auth.ServerAddress
import kotlinx.coroutines.launch

/** Username / password sign-in against the chosen server. */
@Composable
fun LoginScreen() {
    val session = AppSession
    val server by session.serverUrl.collectAsState()
    val remote by session.isRemote.collectAsState()
    val notice by session.loginNotice.collectAsState()
    var username by remember { mutableStateOf(DebugPrefill.username ?: "") }
    var password by remember { mutableStateOf(DebugPrefill.password ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Prefilled (debug) lands on Sign In; otherwise on Username.
    val signInFocus = remember { FocusRequester() }
    val userFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { (if (username.isNotEmpty() && password.isNotEmpty()) signInFocus else userFocus).requestFocus() }
    }

    fun signIn() {
        if (signingIn || username.isEmpty() || password.isEmpty()) return
        signingIn = true
        error = null
        scope.launch {
            try {
                session.signIn(username.trim(), password)
                password = ""
            } catch (e: ApiError) {
                error = if (e.isDefaultPassword) {
                    "This admin account still uses the default password. Change it in the web UI, then sign in here."
                } else {
                    e.message // rate limiting includes Retry-After
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                signingIn = false
            }
        }
    }

    OnboardingLayout(
        title = "Sign In",
        subtitle = if (remote) "Remote access" else server?.let { ServerAddress.display(it) },
    ) {
        if (notice != null && error == null) Text(notice!!, color = Color.White.copy(alpha = 0.6f))
        FormField(username, { username = it }, label = "Username", modifier = Modifier.focusRequester(userFocus))
        FormField(password, { password = it }, label = "Password", password = true, imeAction = ImeAction.Done, onSubmit = ::signIn)
        error?.let { OnboardingError(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BusyButton("Sign In", busy = signingIn, enabled = username.isNotEmpty() && password.isNotEmpty(),
                       modifier = Modifier.focusRequester(signInFocus), onClick = ::signIn)
            OutlinedButton(onClick = { session.changeServer() }, enabled = !signingIn) { Text("Change Server") }
        }
    }
}
