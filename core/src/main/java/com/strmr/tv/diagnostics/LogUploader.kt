package com.strmr.tv.diagnostics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.Config
import com.strmr.tv.app.LogFile
import com.strmr.tv.player.PlaybackCoordinator
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Sends the newest part of `strmr.log` to the server (`POST /api/logs/frontend`), as the Expo
 * app does: once a profile is in use, then every minute while the log has grown and no player
 * is up. The server keeps the latest upload per device; the admin UI's Logs page packages it
 * with the backend log for a bug report, so nobody needs a cable or adb to read it.
 */
object LogUploader {
    sealed interface Status {
        data object Idle : Status
        data object Uploading : Status
        data class Uploaded(val at: Instant) : Status
        data class Failed(val message: String) : Status
    }

    /** The last upload's outcome, shown in Settings › About. */
    var status by mutableStateOf<Status>(Status.Idle)
        private set

    private const val MAX_BYTES = 1 shl 20
    private val scope = MainScope()
    private var loop: Job? = null
    private var uploadedThrough: Long? = null
    private var inFlight = false

    /** Starts the periodic upload; with one running, the new profile session's upload goes out at once. */
    fun start() {
        if (loop != null) {
            scope.launch { upload("profile") }
            return
        }
        loop = scope.launch {
            while (isActive) {
                upload("interval")
                delay(60_000)
            }
        }
    }

    /** Uploads now, even when nothing new was logged: Settings' Upload Logs. */
    fun uploadNow() {
        scope.launch { upload("manual", force = true) }
    }

    private suspend fun upload(reason: String, force: Boolean = false) {
        if (inFlight || AppSession.phase.value != AppSession.Phase.Ready) return
        // Off during playback: the player owns the network, and its lines go out after.
        if (!force && PlaybackCoordinator.playerOpen) return
        val (text, written) = LogFile.tail(MAX_BYTES)
        if (text.isEmpty() || (!force && written == uploadedThrough)) return
        inFlight = true
        status = Status.Uploading
        try {
            val marker = "${Instant.now()} I [LogUpload] snapshot reason=$reason bytes=${text.length}"
            ApiClient.sendRaw("POST", "/api/logs/frontend", body = buildJsonObject {
                put("frontendLogs", text + marker)
                put("deviceType", Config.DEVICE_TYPE)
                put("os", Config.osVersion)
                put("appVersion", Config.fullVersion)
            }, promptOnLock = false)
            uploadedThrough = written
            status = Status.Uploaded(Instant.now())
        } catch (e: Exception) {
            status = Status.Failed(e.message ?: "Upload failed")
            AppLog.w("LogUpload", "upload ($reason) failed: ${e.message}")
        } finally {
            inFlight = false
        }
    }
}
