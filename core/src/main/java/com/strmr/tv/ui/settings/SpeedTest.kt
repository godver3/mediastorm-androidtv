package com.strmr.tv.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.adaptive.AdaptiveThroughput
import com.strmr.tv.adaptive.ThroughputSource
import com.strmr.tv.adaptive.ThroughputStore
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.adaptivePlaybackEnabled
import com.strmr.tv.app.AppLog
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Settings › Server: Expo's "Test Connection Speed" (its Settings page's speed test: 4 streams,
 * 8 → 32 → 128 MB until a sample takes 1.5 s) and, read-only, the adaptive playback estimate the
 * app sends with searches. With adaptive playback on, a manual result becomes the route's
 * estimate (`manual`), as in Expo.
 */
object SpeedTestState {
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
