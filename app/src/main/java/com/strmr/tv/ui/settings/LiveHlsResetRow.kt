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
            onClick = { result = resetResult(LiveHlsPreference.resetAll()) },
            headlineContent = { Text("Try Direct Again") },
            supportingContent = { Text("Forget live sources switched to HLS, so they try their own stream format on the next tune") },
            trailingContent = { result?.let { Text(it) } },
        )
    }
}

private fun resetResult(count: Int): String = when (count) {
    0 -> "Nothing to reset"
    1 -> "Reset 1 source"
    else -> "Reset $count sources"
}
