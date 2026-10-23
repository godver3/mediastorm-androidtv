package com.strmr.tv.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.adaptive.AdaptiveThroughput
import com.strmr.tv.adaptive.ThroughputSource
import com.strmr.tv.adaptive.ThroughputRules
import com.strmr.tv.adaptive.ThroughputStore
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.adaptivePlaybackEnabled
import com.strmr.tv.app.AppLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Settings › Server: Expo's "Test Connection Speed" (its Settings page's speed test: 4 streams,
 * 8 → 32 → 128 MB until a sample takes 1.5 s) and, read-only, the adaptive playback estimate the
 * app sends with searches. With adaptive playback on, a manual result becomes the route's
 * estimate (`manual`), as in Expo.
 */
private object SpeedTestState {
    sealed interface Status {
        data object Idle : Status
        data object Running : Status
        data class Done(val mbps: Double, val elapsedMs: Long, val at: Instant) : Status
        data class Failed(val message: String) : Status
    }

    var status by mutableStateOf<Status>(Status.Idle)
    /** `filtering.adaptivePlaybackEnabled` (profile, else server); null until read. */
    var adaptiveEnabled by mutableStateOf<Boolean?>(null)
    private val scope = MainScope()

    fun run() {
        if (status == Status.Running) return
        status = Status.Running
        scope.launch {
            status = try {
                val sample = AdaptiveThroughput.escalate(AdaptiveThroughput.MANUAL_SAMPLES, AdaptiveThroughput.MANUAL_MIN_ELAPSED_MS)
                AppLog.i("Adaptive", "manual speed test result mbps=${"%.1f".format(sample.speedMbps)} " +
                    "bytes=${sample.receivedBytes} elapsedMs=${sample.elapsedMs.toLong()}")
                val enabled = runCatching { ApiClient.adaptivePlaybackEnabled() }.getOrDefault(false)
                adaptiveEnabled = enabled
                if (enabled) runCatching { ThroughputStore.save(sample.speedMbps, ThroughputSource.MANUAL) }
                    .onFailure { AppLog.w("Adaptive", "manual estimate not saved: ${it.message}") }
                Status.Done(sample.speedMbps, sample.elapsedMs.toLong(), Instant.now())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Adaptive", "manual speed test failed: ${e.message}")
                Status.Failed(e.message ?: "Speed test failed")
            }
        }
    }
}

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
