package com.strmr.tv.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.adaptive.ThroughputSource
import com.strmr.tv.adaptive.ThroughputRules
import com.strmr.tv.adaptive.ThroughputStore
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.adaptivePlaybackEnabled
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun time(at: Instant): String =
    at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))

fun LazyListScope.speedTestRows() {
    item {
        val status = SpeedTestState.status
        ListItem(
            selected = false, onClick = { SpeedTestState.run() },
            headlineContent = { Text("Test Connection Speed") },
            supportingContent = {
                Text(when (status) {
                    SpeedTestState.Status.Idle -> "Download from the server to measure this device's speed"
                    SpeedTestState.Status.Running -> "Testing…"
                    is SpeedTestState.Status.Done -> "Measured ${time(status.at)} in ${"%.1f".format(status.elapsedMs / 1000.0)} s"
                    is SpeedTestState.Status.Failed -> status.message
                })
            },
            trailingContent = { if (status is SpeedTestState.Status.Done) Text("${"%.1f".format(status.mbps)} Mbps") },
        )
    }
    item {
        LaunchedEffect(Unit) {
            SpeedTestState.adaptiveEnabled = runCatching { ApiClient.adaptivePlaybackEnabled() }.getOrNull()
        }
        val enabled = SpeedTestState.adaptiveEnabled
        @Suppress("UNUSED_VARIABLE") val revision = ThroughputStore.revision // re-read after a save
        val estimate = ThroughputStore.stored()
        val fresh = ThroughputRules.isFresh(estimate, System.currentTimeMillis())
        ListItem(
            selected = false, onClick = {},
            headlineContent = { Text("Adaptive Playback Estimate") },
            supportingContent = {
                Text(when {
                    enabled == false -> "Adaptive playback is off"
                    estimate == null -> "Not measured yet on this connection"
                    else -> (if (estimate.source == ThroughputSource.MANUAL) "Speed test" else "Automatic test") +
                        " · ${time(Instant.ofEpochSecond(estimate.measuredAt))}" + if (fresh) "" else " · expired, not sent"
                })
            },
            trailingContent = { estimate?.let { Text("${ThroughputRules.headerValue(it.measuredMbps)} Mbps") } },
        )
    }
}
