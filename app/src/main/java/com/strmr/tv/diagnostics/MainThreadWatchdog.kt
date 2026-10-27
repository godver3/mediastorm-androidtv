package com.strmr.tv.diagnostics

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.strmr.tv.app.AppLog
import com.strmr.tv.update.AppForeground

/**
 * Logs when the main thread stops answering, with the stack it is stuck in, from a thread that
 * keeps running while it is. A hang long enough becomes an ANR, and on devices below API 30
 * (Fire OS 7) the system keeps no record of it that [CrashReporter] could read later. Only one
 * ping is ever outstanding, and none while the app is off screen, where a frozen process is
 * expected.
 */
object MainThreadWatchdog {
    private const val AREA = "watchdog"
    private const val THRESHOLD_MS = 2_000L
    private const val REPEAT_MS = 5_000L
    private const val MAX_FRAMES = 25
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var pingSentAt = 0L
    @Volatile private var lastReportAt = 0L

    fun start() {
        Thread({ loop() }, "strmr-watchdog").apply { isDaemon = true }.start()
    }

    private fun loop() {
        while (true) {
            Thread.sleep(1_000)
            val now = SystemClock.uptimeMillis()
            if (!AppForeground.visible) {
                pingSentAt = 0
                continue
            }
            val sentAt = pingSentAt
            if (sentAt == 0L) {
                pingSentAt = now
                main.post { answered(now) }
                continue
            }
            val stalled = now - sentAt
            if (stalled < THRESHOLD_MS || (lastReportAt != 0L && now - lastReportAt < REPEAT_MS)) continue
            lastReportAt = now
            val stack = Looper.getMainLooper().thread.stackTrace.take(MAX_FRAMES).joinToString("\n  at ")
            AppLog.e(AREA, "main thread unresponsive for ${stalled}ms, at:\n  at $stack")
        }
    }

    private fun answered(sentAt: Long) {
        if (pingSentAt != sentAt) return
        val stalled = SystemClock.uptimeMillis() - sentAt
        if (stalled >= THRESHOLD_MS) AppLog.e(AREA, "main thread answered after ${stalled}ms")
        pingSentAt = 0
        lastReportAt = 0
    }
}
