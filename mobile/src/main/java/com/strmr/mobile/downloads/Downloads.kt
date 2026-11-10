package com.strmr.mobile.downloads

import com.strmr.mobile.connection.ConnectionManager
import com.strmr.mobile.connection.NetworkMonitor
import com.strmr.mobile.downloads.DownloadItem.Status
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.TrackSelection
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.AppSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A download couldn't be queued (not enough storage). */
class DownloadError(message: String) : Exception(message)

/**
 * Offline downloads (the iPhone app's `DownloadManager`): the records, persisted as JSON; a
 * queue that runs up to [DownloadSettings.maxConcurrent] transfers ([DownloadTransfer]) while
 * signed in with the server answering and, with Wi-Fi Only on, an unmetered network; pause,
 * resume, delete, automatic retry with backoff, and the disk-space check before a download
 * starts. [DownloadService] keeps the process alive (with the progress notification) while any
 * download is queued or running. Main thread only.
 */
object Downloads {
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items
    private val _usedBytes = MutableStateFlow(0L)
    /** Bytes the downloads take on disk. */
    val usedBytes: StateFlow<Long> = _usedBytes

    private val scope = MainScope()
    private val transfers = mutableMapOf<String, Job>()
    private val samples = mutableMapOf<String, Pair<Long, Long>>()
    private var retryTimer: Job? = null
    private var persistJob: Job? = null
    private var started = false

    /** Loads the records and starts following the network, the connection and the settings. Idempotent. */
    fun start() {
        if (started) return
        started = true
        _items.value = DownloadRecords.load().map(DownloadQueue::relaunched)
            .filterNot { it.isCompleted && !DownloadStorage.fileExists(it.fileName) }
        val known = _items.value.flatMap { listOf(it.fileName, "${it.id}.part", "${it.id}.jpg") }.toSet()
        scope.launch(Dispatchers.IO) {
            // Leftovers of deleted downloads (a transfer still writing as it was cancelled).
            DownloadStorage.directory.listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
            withContext(Dispatchers.Main) { refreshUsage() }
        }
        scope.launch {
            combine(NetworkMonitor.path, ConnectionManager.status, AppSession.phase, DownloadSettings.changes) { _, _, _, _ -> }
                .collect { processQueue() }
        }
    }

    // Lookup

    fun item(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    fun find(titleId: String, season: Int?, episode: Int?): DownloadItem? =
        _items.value.firstOrNull { it.matches(titleId, season, episode) }

    /** The movie's download, or the episode's. */
    fun find(target: DetailsTarget, episode: Episode?): DownloadItem? =
        if (target.isMovie) find(target.titleId, null, null) else episode?.let { find(target.titleId, it.seasonNumber, it.episodeNumber) }

    /** The movie's (or episode's) download as it changes; null while there is none. */
    fun state(target: DetailsTarget, episode: Episode?): Flow<DownloadItem?> =
        items.map { find(target, episode) }.distinctUntilChanged()

    val completed: List<DownloadItem> get() = _items.value.filter { it.isCompleted }

    // Start

    /**
     * Queues a download of the movie (or episode) at `streamPath`. An existing download of it
     * is kept when finished or running, resumed when paused, and replaced when it failed.
     * Throws [DownloadError] when the volume lacks room for `expectedBytes` plus 10%.
     */
    fun start(
        target: DetailsTarget, episode: Episode?, streamPath: String, releaseName: String?, expectedBytes: Long,
        tracks: TrackSelection? = null,
    ): DownloadItem {
        start()
        val ep = if (target.isMovie) null else episode
        find(target, ep)?.let { existing ->
            when (existing.status) {
                Status.COMPLETED, Status.DOWNLOADING, Status.PENDING -> return existing
                Status.PAUSED -> return existing.also { resume(it.id) }
                Status.FAILED -> delete(existing.id)
            }
        }
        val free = DownloadStorage.availableBytes
        if (!DownloadStorage.hasRoom(expectedBytes, free)) {
            throw DownloadError("Not enough storage for this download: it needs ${DownloadFormat.bytes(expectedBytes)} " +
                "and ${DownloadFormat.bytes(free)} is free.")
        }
        val item = DownloadItem.create(target, ep, streamPath, releaseName, expectedBytes, tracks)
        _items.value = _items.value + item
        AppLog.i("Downloads", "queued ${item.displayTitle} ($expectedBytes bytes)")
        persistNow()
        scope.launch(Dispatchers.IO) { DownloadStorage.savePoster(target.posterUrl, item.id) }
        processQueue()
        return item
    }

    // Pause, resume, delete

    fun pause(id: String) {
        val next = item(id)?.let(DownloadQueue::paused) ?: return
        stopTransfer(id)
        replace(next)
        persistNow()
        processQueue()
    }

    /** Resume a paused download, or retry a failed one (from its partial file). */
    fun resume(id: String) {
        val next = item(id)?.let(DownloadQueue::resumed) ?: return
        replace(next)
        persistNow()
        processQueue()
    }

    fun delete(id: String) = delete(listOf(id))

    fun delete(ids: Collection<String>) {
        val gone = _items.value.filter { it.id in ids }
        if (gone.isEmpty()) return
        gone.forEach { stopTransfer(it.id) }
        _items.value = _items.value.filterNot { it.id in ids }
        scope.launch(Dispatchers.IO) {
            gone.forEach { DownloadStorage.removeFiles(it.id, it.fileName) }
            withContext(Dispatchers.Main) { refreshUsage() }
        }
        gone.forEach { OfflineProgress.forget(it.id) }
        AppLog.i("Downloads", "deleted ${gone.size} download(s)")
        persistNow()
        processQueue()
    }

    // Queue

    private fun processQueue() {
        if (!started) return
        val path = NetworkMonitor.path.value
        val conditions = DownloadQueue.Conditions(
            maxConcurrent = DownloadSettings.maxConcurrent,
            wifiOnly = DownloadSettings.wifiOnly,
            unmetered = path.satisfied && !path.metered,
            serverReachable = AppSession.phase.value == AppSession.Phase.Ready &&
                ConnectionManager.status.value == ConnectionManager.Status.Online,
            now = System.currentTimeMillis(),
        )
        val plan = DownloadQueue.plan(_items.value, conditions)
        plan.hold.forEach { id ->
            stopTransfer(id)
            update(id) { it.copy(status = Status.PENDING, bytesPerSecond = 0.0) }
        }
        plan.waiting.forEach { (id, reason) -> if (item(id)?.waitingReason != reason) update(id) { it.copy(waitingReason = reason) } }
        plan.start.forEach { id -> item(id)?.let(::launchTransfer) }
        retryTimer?.cancel()
        plan.nextRetryAt?.let { at ->
            retryTimer = scope.launch {
                delay((at - System.currentTimeMillis()).coerceAtLeast(0) + 100)
                processQueue()
            }
        }
        DownloadService.sync(_items.value)
        persistSoon()
    }

    private fun launchTransfer(item: DownloadItem) {
        update(item.id) { it.copy(status = Status.DOWNLOADING, waitingReason = null, retryAt = null) }
        samples.remove(item.id)
        AppLog.i("Downloads", "starting ${item.displayTitle}")
        transfers[item.id] = scope.launch {
            val outcome = DownloadTransfer(item) { written, total ->
                scope.launch { progressed(item.id, written, total) }
            }.run()
            if (transfers[item.id] != coroutineContext[Job]) return@launch
            transfers.remove(item.id)
            samples.remove(item.id)
            finished(item.id, outcome)
        }
    }

    private fun progressed(id: String, written: Long, total: Long?) {
        if (id !in transfers) return
        val now = System.nanoTime()
        val last = samples[id]
        samples[id] = written to now
        update(id) { item ->
            val speed = if (last != null && written >= last.first) {
                DownloadQueue.speed(item.bytesPerSecond, written - last.first, (now - last.second) / 1e9)
            } else item.bytesPerSecond
            item.copy(bytesWritten = written, expectedBytes = total?.takeIf { it > 0 } ?: item.expectedBytes, bytesPerSecond = speed)
        }
        DownloadService.sync(_items.value)
        persistSoon()
    }

    private fun finished(id: String, outcome: DownloadTransfer.Outcome) {
        val current = item(id) ?: return
        when (outcome) {
            is DownloadTransfer.Outcome.Completed -> {
                AppLog.i("Downloads", "finished ${current.displayTitle}: ${outcome.bytes} bytes")
                replace(current.copy(status = Status.COMPLETED, bytesWritten = outcome.bytes, expectedBytes = outcome.bytes,
                                     bytesPerSecond = 0.0, completedAt = System.currentTimeMillis(), waitingReason = null,
                                     errorMessage = null, retries = 0))
                DownloadNotifications.completed(current)
                refreshUsage()
            }
            is DownloadTransfer.Outcome.Failed -> {
                AppLog.w("Downloads", "${current.displayTitle} failed: ${outcome.message} (retryable ${outcome.retryable})")
                val next = DownloadQueue.afterFailure(current, outcome.message, outcome.retryable, System.currentTimeMillis())
                replace(next)
                if (next.status == Status.FAILED) DownloadNotifications.failed(next)
            }
        }
        persistNow()
        processQueue()
    }

    private fun stopTransfer(id: String) {
        transfers.remove(id)?.cancel()
        samples.remove(id)
    }

    // State

    private fun update(id: String, change: (DownloadItem) -> DownloadItem) {
        _items.value = _items.value.map { if (it.id == id) change(it) else it }
    }

    private fun replace(item: DownloadItem) = update(item.id) { item }

    fun refreshUsage() {
        scope.launch {
            _usedBytes.value = withContext(Dispatchers.IO) { DownloadStorage.usedBytes() }
        }
    }

    /** Writes the records soon, once per burst of progress updates. */
    private fun persistSoon() {
        if (persistJob?.isActive == true) return
        persistJob = scope.launch {
            delay(5_000)
            persistNow()
        }
    }

    fun persistNow() {
        persistJob?.cancel()
        persistJob = null
        val snapshot = _items.value
        scope.launch(Dispatchers.IO) { DownloadRecords.save(snapshot) }
    }
}
