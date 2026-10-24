package com.strmr.tv.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.live.LiveHlsPreference

/**
 * Settings › Playback: forgets the live sources remembered as needing HLS
 * ([LiveHlsPreference]), so their next tune tries the source's own setting again.
 */
fun LazyListScope.liveHlsResetRow() {
    item {
        var result by remember { mutableStateOf<String?>(null) }
        ListItem(
            selected = false,
            onClick = { result = if (LiveHlsPreference.resetAll() == 0) "Nothing to reset" else "Reset" },
            headlineContent = { Text("Reset Live TV Playback") },
            supportingContent = { Text("Try your provider's own stream format again on the next tune") },
            trailingContent = { result?.let { Text(it) } },
        )
    }
}
