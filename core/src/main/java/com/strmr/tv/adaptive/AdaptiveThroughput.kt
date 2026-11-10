package com.strmr.tv.adaptive

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.adaptivePlaybackEnabled
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.AppSession
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.update.AppForeground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The automatic idle speed test of Expo's `refreshAdaptiveThroughputEstimate`, with the
 * triggers of its `BackendSettingsContext`: 15 s after the profile's settings load and 15 s
 * after the app comes back to the foreground, only while adaptive playback is on (the
 * profile's `filtering.adaptivePlaybackEnabled`, else the server's) and the route has no fresh
 * estimate. Never while a stream resolves or plays: starting playback cancels a pending or
 * running test. Main thread.
 */
object AdaptiveThroughput {
    /** Automatic probes favour low impact: 4 streams, 4 → 16 → 64 MB until a sample takes 750 ms. */
    val IDLE_SAMPLES = listOf(4L shl 20, 16L shl 20, 64L shl 20)
    const val IDLE_MIN_ELAPSED_MS = 750.0
    /** Settings' test (Expo's `CONNECTION_SPEED_TEST_*`): 8 → 32 → 128 MB until a sample takes 1.5 s. */
    val MANUAL_SAMPLES = listOf(8L shl 20, 32L shl 20, 128L shl 20)
    const val MANUAL_MIN_ELAPSED_MS = 1500.0
    const val PARALLEL_STREAMS = 4
    private const val IDLE_DELAY_MS = 15_000L

    private val scope = MainScope()
    private var timer: Job? = null
    private var running: Job? = null

    /** Restarts the 15 s idle timer (a later trigger replaces an earlier one, as in Expo). */
    fun scheduleIdle(trigger: String) {
        timer?.cancel()
        timer = scope.launch {
            delay(IDLE_DELAY_MS)
            timer = null
            val skip = when {
                !AppForeground.visible -> "app in background"
                playbackBusy -> "playback active"
                AppSession.phase.value != AppSession.Phase.Ready -> "no profile"
                running?.isActive == true -> "already running"
                else -> null
            }
            if (skip != null) {
                AppLog.i("Adaptive", "idle refresh skipped trigger=$trigger: $skip")
                return@launch
            }
            running = scope.launch { refresh(force = false, trigger = trigger) }
        }
    }

    /** A stream is starting: no benchmark competes with it. */
    fun playbackStarting() {
        timer?.cancel()
        timer = null
        if (running?.isActive == true) {
            AppLog.i("Adaptive", "idle speed test cancelled: playback starting")
            running?.cancel()
        }
        running = null
    }

    private val playbackBusy: Boolean
        get() = PlaybackCoordinator.playerOpen || PlaybackCoordinator.resolving.value != null

    suspend fun refresh(force: Boolean, trigger: String) {
        if (!ApiClient.adaptivePlaybackEnabled()) {
            AppLog.i("Adaptive", "idle refresh skipped trigger=$trigger: adaptive playback is off")
            return
        }
        val route = ThroughputStore.routeKey
        if (!force && ThroughputStore.fresh(route) != null) {
            AppLog.i("Adaptive", "idle refresh skipped trigger=$trigger: route estimate is fresh")
            return
        }
        AppLog.i("Adaptive", "idle speed test started route=$route trigger=$trigger force=$force " +
            "samples=$IDLE_SAMPLES streams=$PARALLEL_STREAMS minElapsedMs=${IDLE_MIN_ELAPSED_MS.toLong()}")
        try {
            val sample = escalate(IDLE_SAMPLES, IDLE_MIN_ELAPSED_MS)
            if (playbackBusy) {
                AppLog.i("Adaptive", "idle measurement dropped: playback started during the test")
                return
            }
            val saved = ThroughputStore.save(sample.speedMbps, ThroughputSource.IDLE, route)
            AppLog.i("Adaptive", "idle measurement accepted mbps=${saved.measuredMbps} bytes=${sample.receivedBytes} " +
                "transferMs=${sample.transferMs.toLong()} sampleId=${sample.sampleId}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Adaptive", "idle measurement failed: ${e.message}")
        }
    }

    /**
     * Runs samples of growing size until one's transfer takes at least `minElapsedMs` (a short
     * sample over a fast link mostly measures setup), and returns the last.
     */
    suspend fun escalate(samples: List<Long>, minElapsedMs: Double): SpeedSample {
        var last: SpeedSample? = null
        for (bytes in samples) {
            val sample = SpeedTest.aggregate(bytes, PARALLEL_STREAMS)
            last = sample
            if (sample.transferMs >= minElapsedMs) break
        }
        return last ?: error("no samples")
    }
}
