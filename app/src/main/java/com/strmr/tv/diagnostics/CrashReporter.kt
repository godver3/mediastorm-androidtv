package com.strmr.tv.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.Config
import java.io.File
import java.time.Instant

/**
 * Crash evidence for remote reports. A crash ends the process before the log can say anything,
 * so a submitted log showed only a gap and a relaunch. Two sources fill it in on the next launch,
 * logged under `[crash]` so they ride the next upload:
 *
 * - an uncaught Kotlin/Java exception, written synchronously to `cache/strmr_last_crash.log` by
 *   the default handler before the process dies (every API level);
 * - the system's record of why earlier processes ended ([ApplicationExitInfo], API 30+): native
 *   crashes (with the crashing thread's backtrace from the tombstone, API 31+), ANRs (with the
 *   main thread's stack from the system's dump), and low-memory kills.
 */
object CrashReporter {
    private const val AREA = "crash"
    private const val PREFS_KEY = "crash.lastExitTimestamp"
    private const val MAX_EXITS = 10
    private const val MAX_ANR_LINES = 60
    private val file by lazy { File(App.context.cacheDir, "strmr_last_crash.log") }

    fun install() {
        AppLog.i(AREA, "launch ${Config.fullVersion} ${Config.osVersion} ${Build.MANUFACTURER} ${Build.MODEL}")
        reportPreviousCrash()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { reportExits() }.onFailure { AppLog.e(AREA, "exit reasons unavailable", it) }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Written on the dying thread, without the log's executor: the process ends right after. */
    private fun record(thread: Thread, error: Throwable) {
        val text = buildString {
            append("=== crash: ${Config.fullVersion} ${Config.osVersion} at ${Instant.now()}\n")
            append("uncaught on thread \"${thread.name}\": ")
            append(error.stackTraceToString())
        }
        file.writeText(text)
    }

    private fun reportPreviousCrash() {
        if (!file.exists()) return
        val text = runCatching { file.readText() }.getOrDefault("")
        file.delete()
        AppLog.e(AREA, "the previous run crashed; its report follows")
        text.lineSequence().filter { it.isNotBlank() }.forEach { AppLog.e(AREA, it) }
    }

    /** Every process exit since the last launch looked, the first launch only reporting the latest. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun reportExits() {
        val context = App.context
        val prefs = context.getSharedPreferences("strmr", Context.MODE_PRIVATE)
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
        val lastSeen = prefs.getLong(PREFS_KEY, -1)
        val fresh = if (lastSeen < 0) exits.take(1) else exits.filter { it.timestamp > lastSeen }
        exits.firstOrNull()?.let { prefs.edit().putLong(PREFS_KEY, it.timestamp).apply() }
        for (exit in fresh.reversed()) {
            val line = "previous process exit: ${reasonName(exit.reason)} at ${Instant.ofEpochMilli(exit.timestamp)}" +
                " status=${exit.status} importance=${exit.importance} pss=${exit.pss / 1024}MB rss=${exit.rss / 1024}MB" +
                (exit.description?.let { " description=$it" } ?: "")
            if (isAbnormal(exit.reason)) AppLog.e(AREA, line) else AppLog.i(AREA, line)
            if (exit.reason == ApplicationExitInfo.REASON_ANR) reportAnrStack(exit)
            if (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                reportTombstone(exit)
            }
        }
    }

    /** The crashing thread's native backtrace, from the tombstone the system kept. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun reportTombstone(exit: ApplicationExitInfo) {
        val lines = runCatching { exit.traceInputStream?.use { TombstoneReader.summary(it) } }
            .onFailure { AppLog.e(AREA, "tombstone unreadable", it) }
            .getOrNull() ?: return
        lines.forEach { AppLog.e(AREA, it) }
    }

    /** The main thread's block of the system's ANR dump: where it was stuck. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun reportAnrStack(exit: ApplicationExitInfo) {
        val trace = runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: return
        val lines = trace.lineSequence()
            .dropWhile { !it.startsWith("\"main\"") }
            .takeWhile { it.isNotBlank() }
            .take(MAX_ANR_LINES)
            .toList()
        if (lines.isEmpty()) return
        AppLog.e(AREA, "ANR main thread:")
        lines.forEach { AppLog.e(AREA, it) }
    }

    private fun isAbnormal(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE, ApplicationExitInfo.REASON_SIGNALED -> true
        else -> false
    }

    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit-self"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low-memory"
        ApplicationExitInfo.REASON_CRASH -> "crash (java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash (native)"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization-failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission-change"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive-resource-usage"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user-requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user-stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency-died"
        ApplicationExitInfo.REASON_OTHER -> "other"
        // Added after API 30; by value so older SDK constants aren't needed.
        14 -> "freezer"
        15 -> "package-state-change"
        16 -> "package-updated"
        else -> "unknown($reason)"
    }
}
