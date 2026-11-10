package com.strmr.mobile.ui.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.strmr.tv.api.ApiError
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.DebugPrefill
import com.strmr.tv.auth.ServerAddress
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val session = AppSession
    val server by session.serverUrl.collectAsState()
    val remote by session.isRemote.collectAsState()
    val notice by session.loginNotice.collectAsState()
    var username by rememberSaveable { mutableStateOf(DebugPrefill.username ?: "") }
    var password by remember { mutableStateOf(DebugPrefill.password ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun signIn() {
        if (signingIn || username.isBlank() || password.isEmpty()) return
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
                    e.message
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                signingIn = false
            }
        }
    }

    OnboardingScaffold(
        title = "Sign In",
        subtitle = if (remote) "Remote access" else server?.let { ServerAddress.display(it) },
    ) {
        if (notice != null && error == null) {
            Text(notice!!, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { signIn() }),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        )
        error?.let { ErrorText(it) }
        BusyButton("Sign In", busy = signingIn, enabled = username.isNotBlank() && password.isNotEmpty(), onClick = ::signIn)
        TextButton(onClick = { session.changeServer() }, enabled = !signingIn) { Text("Change Server") }
    }
}
