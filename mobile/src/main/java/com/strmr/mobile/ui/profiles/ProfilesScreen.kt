package com.strmr.mobile.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.onboarding.PinSheet
import com.strmr.mobile.ui.onboarding.ProfileAvatar
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.api.Profile
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.PinVerification
import kotlinx.coroutines.launch

/**
 * The Profiles section (the Expo and iPhone phone apps' Profiles page): the account's profiles,
 * PIN and Kids marked and the active one checked; tapping one switches to it (its PIN first).
 * Profiles are created and edited in the server's web UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(asPage: Boolean) {
    val session = AppSession
    val profiles by session.profiles.collectAsState()
    val current by session.profile.collectAsState()
    val scope = rememberCoroutineScope()
    var pinTarget by remember { mutableStateOf<Profile?>(null) }
    var switching by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun reload() {
        try {
            session.loadProfiles()
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun choose(profile: Profile) {
        if (switching != null || profile.id == current?.id) return
        error = null
        if (profile.requiresPin) {
            pinTarget = profile
            return
        }
        switching = profile.id
        scope.launch {
            try {
                session.select(profile)
            } catch (e: Exception) {
                error = e.message
            } finally {
                switching = null
            }
        }
    }

    PageScaffold("Profiles", showBack = asPage) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; scope.launch { reload(); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileRow(profile, active = profile.id == current?.id, busy = switching == profile.id) { choose(profile) }
                }
                item("footer") {
                    Text(
                        error ?: "Tap a profile to switch who's watching. Profiles are created and edited in the server's web UI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    pinTarget?.let { profile ->
        PinSheet(
            profile = profile,
            verify = { pin ->
                try {
                    if (session.select(profile, pin)) {
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
private fun ProfileRow(profile: Profile, active: Boolean, busy: Boolean, onClick: () -> Unit) {
    val tags = listOfNotNull("Kids".takeIf { profile.isKidsProfile == true }, "PIN".takeIf { profile.requiresPin })
    ListItem(
        headlineContent = { Text(profile.name) },
        supportingContent = if (tags.isEmpty() && !active) null else {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (profile.requiresPin) Icon(Icons.Filled.Lock, null, Modifier.size(14.dp))
                    Text((listOfNotNull("Watching now".takeIf { active }) + tags).joinToString(" · "))
                }
            }
        },
        leadingContent = { ProfileAvatar(profile, 44.dp) },
        trailingContent = {
            when {
                busy -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                active -> Icon(Icons.Filled.Check, "Active", tint = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
