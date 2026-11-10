package com.strmr.tv.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.reportProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A playback's progress reports to the server: every 10 s while playing, and on pause, end and
 * close, with the stream's health. A reply asking for a migration calls [onMigration].
 */
class ProgressReporter(
    private val player: ExoPlayer,
    private val session: PlayerSession,
    private val scope: CoroutineScope,
    private val onMigration: (reason: String) -> Unit,
) {
    private val request = session.request
    private var periodic: Job? = null
    /** The latest report in flight, which pages reloading after playback wait on. */
    var lastReport: Job? = null
        private set
    private val log = ProgressLog(request.title.name + (request.episode?.let { " · ${it.code}" } ?: ""))

    /** Starts the 10 s reports (once). */
    fun start() {
        if (periodic?.isActive == true) return
        periodic = scope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) report()
            }
        }
    }

    fun stop() {
        periodic?.cancel()
    }

    /** Position and duration in seconds, or null before the duration is known. */
    private fun times(): Pair<Double, Double>? {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return null
        return player.currentPosition / 1000.0 to duration / 1000.0
    }

    fun report(paused: Boolean = false, ended: Boolean = false, closing: Boolean = false) {
        if (!session.reportsProgress) return
        val (position, duration) = times() ?: return
        val body = request.progressReport(position, duration, paused, ended, sourcePath = session.streamPath, stream = streamHealth())
        val kind = when {
            ended -> ProgressLog.Kind.END
            closing -> ProgressLog.Kind.CLOSE
            paused -> ProgressLog.Kind.PAUSE
            else -> ProgressLog.Kind.PERIODIC
        }
        lastReport = scope.launch {
            withContext(NonCancellable) {
                try {
                    val reply = ApiClient.reportProgress(body)
                    log.saved(kind, position, duration, reply)
                    if (reply.migrationRequested && !ended && !closing) {
                        onMigration("server: ${reply.migrationReason ?: "migration requested"}")
                    }
                } catch (e: Exception) {
                    log.failed(kind, position, duration, e)
                }
            }
        }
    }

    private fun streamHealth() = StreamHealth(
        buffering = player.playbackState == Player.STATE_BUFFERING,
        bufferAheadSeconds = player.totalBufferedDuration / 1000.0,
        requiredMbps = player.videoFormat?.bitrate?.takeIf { it > 0 }?.let { it / 1_000_000.0 },
    )
}
