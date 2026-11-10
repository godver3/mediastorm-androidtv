package com.strmr.mobile.downloads

import com.strmr.mobile.downloads.DownloadItem.Status
import com.strmr.mobile.downloads.DownloadItem.WaitingReason

/**
 * The queue's decisions, pure for tests: which pending downloads start (oldest first, up to
 * the simultaneous limit), which running ones stop (Wi-Fi Only off Wi-Fi, or the server out of
 * reach) and what each waiting one waits for. [Downloads] applies the plan.
 */
object DownloadQueue {
    data class Conditions(
        val maxConcurrent: Int,
        val wifiOnly: Boolean,
        /** On a network that isn't metered (Wi-Fi or Ethernet). */
        val unmetered: Boolean,
        /** Signed in with the server answering. */
        val serverReachable: Boolean,
        val now: Long,
    )

    data class Plan(
        val start: List<String> = emptyList(),
        /** Running downloads to stop and put back in the queue. */
        val hold: List<String> = emptyList(),
        /** The waiting reason each pending download should show (null: just queued). */
        val waiting: Map<String, WaitingReason?> = emptyMap(),
        /** When the earliest retry comes due, to check the queue again then. */
        val nextRetryAt: Long? = null,
    )

    fun plan(items: List<DownloadItem>, c: Conditions): Plan {
        val holdReason = when {
            c.wifiOnly && !c.unmetered -> WaitingReason.WIFI
            !c.serverReachable -> WaitingReason.SERVER
            else -> null
        }
        val running = items.filter { it.status == Status.DOWNLOADING }
        if (holdReason != null) {
            val waiting = items.filter { it.status == Status.PENDING || it.status == Status.DOWNLOADING }
                .associate { it.id to holdReason }
            return Plan(hold = running.map { it.id }, waiting = waiting)
        }
        val pending = items.filter { it.status == Status.PENDING }.sortedBy { it.createdAt }
        val due = pending.filter { (it.retryAt ?: 0) <= c.now }
        val slots = (c.maxConcurrent.coerceAtLeast(1) - running.size).coerceAtLeast(0)
        val start = due.take(slots).map { it.id }
        val waiting = pending.associate { it.id to if ((it.retryAt ?: 0) > c.now) WaitingReason.RETRY else null }
        val nextRetry = pending.mapNotNull { it.retryAt }.filter { it > c.now }.minOrNull()
        return Plan(start = start, waiting = waiting, nextRetryAt = nextRetry)
    }

    /** Seconds before each automatic retry; after the last the download fails. */
    val retryDelays = listOf(5L, 15L, 30L, 60L, 120L)

    /**
     * What a failed transfer becomes: queued again after a delay when the failure may pass
     * (network, server error) and retries remain, else failed.
     */
    fun afterFailure(item: DownloadItem, message: String, retryable: Boolean, now: Long): DownloadItem =
        if (retryable && item.retries < retryDelays.size) {
            item.copy(status = Status.PENDING, retries = item.retries + 1, retryAt = now + retryDelays[item.retries] * 1000,
                      errorMessage = message, bytesPerSecond = 0.0)
        } else {
            item.copy(status = Status.FAILED, errorMessage = message, retryAt = null, waitingReason = null, bytesPerSecond = 0.0)
        }

    /** Pause → paused; only a queued or running download pauses. */
    fun paused(item: DownloadItem): DownloadItem? =
        if (item.status == Status.PENDING || item.status == Status.DOWNLOADING) {
            item.copy(status = Status.PAUSED, waitingReason = null, retryAt = null, bytesPerSecond = 0.0)
        } else null

    /** Resume a paused download or retry a failed one, with a fresh retry budget. */
    fun resumed(item: DownloadItem): DownloadItem? =
        if (item.status == Status.PAUSED || item.status == Status.FAILED) {
            item.copy(status = Status.PENDING, errorMessage = null, retries = 0, retryAt = null)
        } else null

    /** After a relaunch: transfers that were running when the app went away are queued again. */
    fun relaunched(item: DownloadItem): DownloadItem =
        if (item.status == Status.DOWNLOADING) item.copy(status = Status.PENDING, bytesPerSecond = 0.0) else item

    /** A smoothed transfer speed from the previous estimate and the latest sample. */
    fun speed(previous: Double, bytes: Long, seconds: Double): Double {
        if (seconds <= 0.1 || bytes < 0) return previous
        val instant = bytes / seconds
        return if (previous > 0) previous * 0.3 + instant * 0.7 else instant
    }
}
