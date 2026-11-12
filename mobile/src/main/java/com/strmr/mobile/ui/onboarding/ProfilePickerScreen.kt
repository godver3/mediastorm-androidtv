package com.strmr.mobile.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.tv.api.Profile
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.PinVerification
import kotlinx.coroutines.launch

/** Who's Watching: the account's profiles, "Use automatically", and a PIN sheet for locked ones. */
@Composable
fun ProfilePickerScreen() {
    val session = AppSession
    val profiles by session.profiles.collectAsState()
    var pinTarget by remember { mutableStateOf<Profile?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var automatic by remember { mutableStateOf(session.autoProfileId != null) }
    val scope = rememberCoroutineScope()

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
    BackHandler(enabled = busyId != null) {}

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

    OnboardingScaffold(title = "Who's Watching?", profiles = true) {
        when {
            profiles.isNotEmpty() -> {
                FlowRow(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    profiles.forEach { profile ->
                        ProfileTile(profile, busy = busyId == profile.id) { choose(profile) }
                    }
                }
                ListItem(
                    headlineContent = { Text("Use this profile automatically") },
                    supportingContent = { Text("Open it at launch without asking") },
                    trailingContent = {
                        Switch(checked = automatic, onCheckedChange = {
                            automatic = it
                            // Off forgets the current one straight away, as on the TV.
                            if (!it) session.setAutoProfile(null)
                        })
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                )
            }
            loadError != null -> {
                ErrorText(loadError!!)
                OutlinedButton(onClick = { scope.launch { reload() } }) { Text("Try Again") }
            }
            else -> CircularProgressIndicator()
        }
        actionError?.let { ErrorText(it) }
        if (session.canCancelProfilePick) TextButton(onClick = { session.cancelProfilePick() }) { Text("Cancel") }
    }

    pinTarget?.let { profile ->
        PinSheet(
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

@Composable
private fun ProfileTile(profile: Profile, busy: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProfileAvatar(profile, 88.dp)
            if (busy) CircularProgressIndicator(Modifier.size(88.dp), strokeWidth = 3.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false),
            )
            if (profile.requiresPin) {
                Icon(Icons.Filled.Lock, "PIN", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
