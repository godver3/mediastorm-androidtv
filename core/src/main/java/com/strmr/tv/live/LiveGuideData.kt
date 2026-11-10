package com.strmr.tv.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Live tab's guide data: now / next and guide schedules, fetched in batches for the channels
 * on screen. Schedules come in fixed six-hour chunks from [guideBase], per guide key. Only asked
 * for while the EPG is on ([epgEnabled]). Main thread only; owned by [LiveStore].
 */
class LiveGuideData(private val scope: CoroutineScope, private val epgEnabled: () -> Boolean) {
    /** Now / next by guide key. */
    var nowNext by mutableStateOf<Map<String, EpgNowPlaying>>(emptyMap())
        private set
    /** Guide programmes by guide key, sorted, de-duplicated by start. */
    var schedules by mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap())
        private set

    private val nowNextFetched = mutableMapOf<String, Long>()
    private val nowNextPending = mutableSetOf<String>()
    private var nowNextJob: Job? = null

    val guideBase: Long = halfHour(System.currentTimeMillis()) - HALF_HOUR
    private val chunksLoaded = mutableSetOf<String>()
    private val chunksPending = mutableMapOf<Int, MutableSet<String>>()
    private var chunkJob: Job? = null

    // Now / next

    /** Queues `channel` for a now / next fetch (re-fetched after two minutes). */
    fun needNowNext(channel: LiveChannel) {
        if (!epgEnabled()) return
        val key = channel.tvgId?.ifEmpty { null } ?: return
        val fetched = nowNextFetched[key]
        if (fetched != null && System.currentTimeMillis() - fetched < 120_000) return
        nowNextFetched[key] = System.currentTimeMillis()
        nowNextPending += key
        if (nowNextJob != null) return
        nowNextJob = scope.launch {
            delay(120)
            val keys = nowNextPending.toList()
            nowNextPending.clear()
            nowNextJob = null
            for (batch in keys.chunked(50)) {
                try {
                    nowNext = nowNext + ApiClient.epgNow(batch).associateBy { it.channelId }
                } catch (e: Exception) {
                    batch.forEach { nowNextFetched.remove(it) }
                }
            }
        }
    }

    /** The programme on now, from the guide data or now / next. */
    fun current(channel: LiveChannel, now: Long = System.currentTimeMillis()): EpgProgram? =
        schedules[channel.guideKey]?.firstOrNull { it.isAiring(now) } ?: nowNext[channel.guideKey]?.current?.takeIf { it.isAiring(now) }

    /** Drops now / next, so it is fetched again as channels show. */
    fun clearNowNext() {
        nowNext = emptyMap()
        nowNextFetched.clear()
    }

    // Guide schedules

    /** Queues the guide chunks covering `[from, to)` for `channel`. */
    fun needSchedule(channel: LiveChannel, from: Long, to: Long) {
        if (!epgEnabled()) return
        val key = channel.guideKey
        // Negative chunks lie before [guideBase]: the past, paged into for catch-up.
        val first = Math.floorDiv(from - guideBase, CHUNK).toInt()
        val last = maxOf(first, Math.floorDiv(to - guideBase - 1, CHUNK).toInt())
        var queued = false
        for (index in first..last) {
            if (chunksLoaded.add("$index|$key")) {
                chunksPending.getOrPut(index) { mutableSetOf() } += key
                queued = true
            }
        }
        if (!queued || chunkJob != null) return
        chunkJob = scope.launch {
            delay(150)
            val pending = chunksPending.toSortedMap()
            chunksPending.clear()
            chunkJob = null
            for ((index, keys) in pending) fetchChunk(index, keys.toList())
        }
    }

    private suspend fun fetchChunk(index: Int, keys: List<String>) {
        for (batch in keys.chunked(40)) {
            val result = chunkPrograms(index, batch)
            if (result != null) {
                val merged = schedules.toMutableMap()
                for (key in batch) merged[key] = merge(merged[key].orEmpty(), result[key].orEmpty())
                schedules = merged
            } else {
                batch.forEach { chunksLoaded.remove("$index|$it") }
            }
        }
    }

    /** One chunk's programmes for up to 40 keys; null when the request failed. */
    private suspend fun chunkPrograms(index: Int, keys: List<String>): Map<String, List<EpgProgram>>? {
        val start = guideBase + index * CHUNK
        val offset = Math.floorDiv(start - System.currentTimeMillis(), 60_000L).toInt()
        return try {
            // An hour over the chunk, so a clock skew can't leave a gap between chunks.
            ApiClient.epgSchedule(keys, offset, (CHUNK / 3_600_000).toInt() + 1)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.w("Live", "guide chunk $index failed: ${e.message}")
            null
        }
    }

    /** Fetches every loaded guide chunk and now / next again, replacing what was there. */
    suspend fun refetch() {
        val keysByChunk = chunksLoaded.mapNotNull { entry ->
            val bar = entry.indexOf('|')
            entry.take(bar).toIntOrNull()?.let { it to entry.substring(bar + 1) }
        }.groupBy({ it.first }, { it.second })
        val fresh = mutableMapOf<String, List<EpgProgram>>()
        val failed = mutableSetOf<String>()
        for ((index, keys) in keysByChunk.toSortedMap()) {
            for (batch in keys.chunked(40)) {
                val result = chunkPrograms(index, batch)
                if (result == null) { failed += batch; continue }
                for (key in batch) fresh[key] = merge(fresh[key].orEmpty(), result[key].orEmpty())
            }
        }
        // Keys with a failed chunk keep their old programmes rather than losing part of the day.
        schedules = schedules + fresh.filterKeys { it !in failed }

        val updated = nowNext.toMutableMap()
        for (batch in nowNext.keys.toList().chunked(50)) {
            val entries = runCatching { ApiClient.epgNow(batch) }.getOrNull() ?: continue
            for (entry in entries) {
                updated[entry.channelId] = entry
                nowNextFetched[entry.channelId] = System.currentTimeMillis()
            }
        }
        nowNext = updated
    }

    companion object {
        const val HALF_HOUR = 1_800_000L
        const val CHUNK = 6 * 3_600_000L

        fun halfHour(ms: Long) = ms - ms % HALF_HOUR

        /** Sorted by start, one programme per start time, each cut off where the next begins. */
        private fun merge(old: List<EpgProgram>, new: List<EpgProgram>): List<EpgProgram> {
            val sorted = (old + new).filter { it.stopMs > it.startMs }.associateBy { it.startMs }.values.sortedBy { it.startMs }
            sorted.forEachIndexed { i, p ->
                p.endMs = p.stopMs
                sorted.getOrNull(i + 1)?.let { next -> if (next.startMs < p.stopMs) p.endMs = next.startMs }
            }
            return sorted
        }
    }
}
