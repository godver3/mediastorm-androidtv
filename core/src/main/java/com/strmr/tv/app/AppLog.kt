package com.strmr.tv.app

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The app's log: logcat under one tag per area (`adb logcat -s strmr.Home`), mirrored to
 * `cache/strmr.log` so it can be uploaded from the device (Settings › About › Upload Logs, and
 * [com.strmr.tv.diagnostics.LogUploader]).
 */
object AppLog {
    fun i(area: String, message: String) {
        Log.i("strmr.$area", message)
        LogFile.append("I", area, message)
    }

    fun w(area: String, message: String) {
        Log.w("strmr.$area", message)
        LogFile.append("W", area, message)
    }

    fun e(area: String, message: String, error: Throwable? = null) {
        Log.e("strmr.$area", message, error)
        LogFile.append("E", area, message + (error?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""))
    }
}

/** The log file, written on one thread. Rotated to `strmr.log.1` past 4 MB. */
object LogFile {
    private const val MAX_BYTES = 4L shl 20
    private val executor = Executors.newSingleThreadExecutor()
    private val file by lazy { File(App.context.cacheDir, "strmr.log") }
    private val rotated by lazy { File(App.context.cacheDir, "strmr.log.1") }
    private var stream: FileOutputStream? = null
    /** Bytes written since launch, so an uploader can tell whether anything new was logged. */
    private var written = 0L

    fun append(level: String, area: String, message: String) {
        val line = "${Instant.now()} $level [$area] $message\n".toByteArray()
        executor.execute {
            runCatching {
                val out = stream ?: FileOutputStream(file, true).also { stream = it }
                out.write(line)
                written += line.size
                if (file.length() > MAX_BYTES) rotate()
            }
        }
    }

    /**
     * The newest `maxBytes` of the log (the rotated file first, then the current one), cut at a
     * line start, and the byte count written so far. Read on the writer's thread, so it never
     * sees half a line.
     */
    suspend fun tail(maxBytes: Int): Pair<String, Long> = suspendCancellableCoroutine { cont ->
        executor.execute {
            val data = runCatching {
                stream?.flush()
                var bytes = (if (rotated.exists()) rotated.readBytes() else ByteArray(0)) +
                    (if (file.exists()) file.readBytes() else ByteArray(0))
                if (bytes.size > maxBytes) {
                    bytes = bytes.copyOfRange(bytes.size - maxBytes, bytes.size)
                    val newline = bytes.indexOf('\n'.code.toByte())
                    if (newline >= 0) bytes = bytes.copyOfRange(newline + 1, bytes.size)
                }
                String(bytes, Charsets.UTF_8)
            }.getOrDefault("")
            cont.resume(data to written)
        }
    }

    private fun rotate() {
        stream?.close()
        stream = null
        rotated.delete()
        file.renameTo(rotated)
    }
}
