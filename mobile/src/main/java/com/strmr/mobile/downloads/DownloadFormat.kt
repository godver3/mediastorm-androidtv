package com.strmr.mobile.downloads

import java.util.Locale

/** Byte, speed and time formatting for the Downloads rows (pure, for tests). */
object DownloadFormat {
    fun bytes(value: Long): String {
        val v = value.coerceAtLeast(0).toDouble()
        val units = listOf("bytes", "KB", "MB", "GB", "TB")
        var size = v
        var unit = 0
        while (size >= 1000 && unit < units.lastIndex) {
            size /= 1000
            unit++
        }
        return when {
            unit == 0 -> "${v.toLong()} bytes"
            size >= 100 -> String.format(Locale.US, "%.0f %s", size, units[unit])
            else -> String.format(Locale.US, "%.1f %s", size, units[unit])
        }
    }

    fun speed(bytesPerSecond: Double): String? = if (bytesPerSecond > 1) "${bytes(bytesPerSecond.toLong())}/s" else null

    /** "12m 5s left" / "1h 3m left", or null when there is no estimate. */
    fun eta(item: DownloadItem): String? {
        if (item.bytesPerSecond <= 1 || item.expectedBytes <= item.bytesWritten) return null
        val seconds = ((item.expectedBytes - item.bytesWritten) / item.bytesPerSecond).toLong()
        if (seconds < 1) return null
        val text = when {
            seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
        return "$text left"
    }

    /** The line under a running or waiting download's progress bar. */
    fun progressLine(item: DownloadItem): String {
        val sizes = if (item.expectedBytes > 0) "${bytes(item.bytesWritten)} of ${bytes(item.expectedBytes)}" else bytes(item.bytesWritten)
        return when (item.status) {
            DownloadItem.Status.PENDING -> when (item.waitingReason) {
                DownloadItem.WaitingReason.WIFI -> "Waiting for Wi-Fi"
                DownloadItem.WaitingReason.SERVER -> "Waiting for the server"
                DownloadItem.WaitingReason.RETRY -> "Retrying soon" + (item.errorMessage?.let { " · $it" } ?: "")
                null -> if (item.bytesWritten > 0) "Queued · $sizes" else "Queued"
            }
            DownloadItem.Status.PAUSED -> "Paused · $sizes"
            else -> listOfNotNull("${Math.round(item.progress * 100)}%", sizes, speed(item.bytesPerSecond), eta(item)).joinToString(" · ")
        }
    }
}
