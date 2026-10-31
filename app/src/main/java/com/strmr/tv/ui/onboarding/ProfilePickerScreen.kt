package com.strmr.tv.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.strmr.tv.api.Profile
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.DebugPrefill
import com.strmr.tv.app.PinVerification
import com.strmr.tv.ui.Accent
import kotlinx.coroutines.launch

/** "Who's watching?" row, with the PIN pad for protected profiles. */
@Composable
fun ProfilePickerScreen() {
    val session = AppSession
    val profiles by session.profiles.collectAsState()
    val current by session.profile.collectAsState()
    var pinTarget by remember { mutableStateOf<Profile?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var automatic by remember { mutableStateOf(session.autoProfileId != null) }
    val scope = rememberCoroutineScope()
    val initial = remember { FocusRequester() }

    suspend fun reload() {
        loadError = null
        try {
            session.loadProfiles()
        } catch (e: Exception) {
            loadError = e.message
        }
    }
    LaunchedEffect(Unit) { if (profiles.isEmpty()) reload() }
    if (session.canCancelProfilePick && pinTarget == null) BackHandler { session.cancelProfilePick() }
    // A Back while the pick is on its way (Home about to open) would otherwise leave the app.
    BackHandler(enabled = busyId != null) {}

    // Current or last profile; in debug builds, the secrets.properties profile on a fresh start.
    val initialId = current?.id ?: session.lastProfileId
        ?: profiles.firstOrNull { it.name == DebugPrefill.profileName }?.id ?: profiles.firstOrNull()?.id
    LaunchedEffect(profiles.isNotEmpty()) { if (profiles.isNotEmpty()) runCatching { initial.requestFocus() } }

    fun choose(profile: Profile) {
        if (busyId != null) return
        actionError = null
        if (profile.requiresPin) {
            pinTarget = profile
            return
        }
        busyId = profile.id
        scope.launch {
            try {
                session.select(profile, automatic = automatic)
            } catch (e: Exception) {
                actionError = e.message
            } finally {
                busyId = null
            }
        }
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(height = 110.dp)
        Spacer(Modifier.height(14.dp))
        Text("Who's Watching?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        when {
            profiles.isNotEmpty() -> {
                // Up from the tickbox returns to the profile Down left from.
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileButton(
                            profile, busy = busyId == profile.id, onClick = { choose(profile) },
                            modifier = if (profile.id == initialId) Modifier.focusRequester(initial) else Modifier,
                        )
                    }
                }
                AutoSelectCheckbox(automatic) {
                    automatic = !automatic
                    // Unticking forgets the current one straight away, so Settings → Switch Profile → Cancel turns it off.
                    if (!automatic) session.setAutoProfile(null)
                }
            }
            loadError != null -> {
                OnboardingError(loadError!!)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { scope.launch { reload() } }) { Text("Try Again") }
            }
            else -> CircularProgressIndicator(color = Accent)
        }
        actionError?.let { OnboardingError(it) }
        if (session.canCancelProfilePick) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { session.cancelProfilePick() }) { Text("Cancel") }
        }
    }

    pinTarget?.let { profile ->
        PinEntry(
            profile = profile,
            verify = { pin ->
                try {
                    if (session.select(profile, pin, automatic)) {
                        pinTarget = null
                        PinVerification.Unlocked
                    } else {
                        PinVerification.Wrong
                    }
                } catch (e: Exception) {
                    PinVerification.Failed(e.message ?: "Couldn't check the PIN.")
                }
            },
            onCancel = { pinTarget = null },
        )
    }
}

/** "Use this profile automatically": the next pick opens at launch. */
@Composable
private fun AutoSelectCheckbox(checked: Boolean, onToggle: () -> Unit) {
    OutlinedButton(onClick = onToggle) {
        Box(
            Modifier.size(18.dp)
                .border(BorderStroke(2.dp, LocalContentColor.current), RoundedCornerShape(3.dp))
                .then(if (checked) Modifier.background(LocalContentColor.current, RoundedCornerShape(3.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Use this profile automatically")
    }
}

/** A round avatar that lifts and glows on focus, the name under it. */
@Composable
private fun ProfileButton(profile: Profile, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White))),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.35f), 12.dp)),
            modifier = modifier.size(96.dp).onFocusChanged { focused = it.isFocused },
        ) {
            Box(contentAlignment = Alignment.Center) {
                ProfileAvatar(profile, 96.dp)
                if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (focused) Color.White else Color.White.copy(alpha = 0.6f)
            Text(profile.name, color = tint)
            if (profile.requiresPin) Icon(Icons.Filled.Lock, contentDescription = "Requires a PIN", tint = tint, modifier = Modifier.size(14.dp))
        }
    }
}
