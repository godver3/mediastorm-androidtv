package com.strmr.tv.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What a multiscreen tile plays. Compared by identity: a retune of the same URL is a new source. */
class TileSource(val url: String) {
    override fun toString() = "TileSource($url)"
}

/** One screen. Empty (no channel, not loading) until a channel is tuned into it. */
data class MultiscreenSlot(
    val channel: LiveChannel? = null,
    val source: TileSource? = null,
    /** The server HLS session, kept even when the start then fails, so cleanup can retry it. */
    val sessionId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val isOccupied get() = channel != null || loading
}

/**
 * The multiscreen's screens, a port of the Expo TV `TVMultiscreenSession`: slot indices are
 * stable (only their rectangles change), each slot owns its server session, and every start,
 * removal and close runs through one queue so the transport can recheck the provider's
 * capacity before each start. A per-slot version makes a superseded tune give way to the
 * newer one; a slot's old session is released before its new one starts. At most [maxSlots]
 * screens (the Expo TV screen had no cap). Main thread only: [scope] runs the queue.
 */
class MultiscreenSession(
    private val transport: Transport,
    scope: CoroutineScope,
    private val maxSlots: Int = MAX_SCREENS,
) {
    interface Transport {
        /** Opens `channel` as an HLS session; an empty URL is a start that returned nothing playable. */
        suspend fun start(channel: LiveChannel): LiveStream
        /** Stops a session; throws when the server didn't confirm. */
        suspend fun stop(sessionId: String)
    }

    data class State(val slots: List<MultiscreenSlot>, val audioIndex: Int, val focusedIndex: Int)

    private val slots = mutableListOf(MultiscreenSlot(), MultiscreenSlot())
    private var audioIndex = 0
    private var focusedIndex = 0
    private var closed = false
    private val versions = mutableListOf(0, 0)
    /** The channel each slot's newest tune asked for, while it is in flight (duplicates count it). */
    private val requested = mutableListOf<LiveChannel?>(null, null)
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<State> = _state

    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { for (operation in queue) operation() }
    }

    private fun snapshot() = State(slots.toList(), audioIndex, focusedIndex)

    private fun publish() {
        _state.value = snapshot()
    }

    private fun <T> enqueue(operation: suspend () -> T): Deferred<T> {
        val result = CompletableDeferred<T>()
        queue.trySend {
            try {
                result.complete(operation())
            } catch (e: CancellationException) {
                result.cancel(e)
                throw e
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        }
        return result
    }

    private suspend fun release(index: Int) {
        slots[index].sessionId?.let { transport.stop(it) }
        slots[index] = MultiscreenSlot()
    }

    private fun valid(index: Int) = index in slots.indices

    /** The slot showing (or tuning) `channel`, or -1. */
    fun indexOf(channel: LiveChannel): Int = slots.indices.firstOrNull { index ->
        val candidate = requested[index] ?: slots[index].channel
        candidate != null && candidate.id == channel.id && candidate.sourceId == channel.sourceId
    } ?: -1

    /**
     * Takes over a stream another player opened (the full-screen live player's HLS session) as
     * the first screen; this session stops it like its own. Ignored once the first screen is in use.
     */
    fun adopt(channel: LiveChannel, stream: LiveStream) {
        if (closed || slots[0].isOccupied) return
        slots[0] = MultiscreenSlot(channel, TileSource(stream.url), stream.sessionId)
        publish()
    }

    fun focus(index: Int) {
        if (!valid(index)) return
        focusedIndex = index
        publish()
    }

    /** Moves the sound to `index`, if it is playing something. */
    fun listen(index: Int) {
        if (slots.getOrNull(index)?.source == null) return
        audioIndex = index
        publish()
    }

    /**
     * Tunes `channel` into slot `index` (one past the last adds a screen). A channel already on
     * another screen focuses that screen instead.
     */
    fun tune(index: Int, channel: LiveChannel): Deferred<Unit> {
        if (closed || index < 0 || index > slots.size || index >= maxSlots) return done()
        val duplicate = indexOf(channel)
        if (duplicate != -1 && duplicate != index) {
            focus(duplicate)
            return done()
        }
        if (index == slots.size) {
            slots += MultiscreenSlot()
            versions += 0
            requested += null
        }
        val version = ++versions[index]
        requested[index] = channel
        slots[index] = slots[index].copy(loading = true, error = null)
        publish()
        return enqueue {
            if (closed || version != versions[index]) return@enqueue
            try {
                release(index)
                if (closed || version != versions[index]) return@enqueue
                slots[index] = MultiscreenSlot(channel, loading = true)
                publish()
                val prepared = transport.start(channel)
                // Kept even if stale, so a failed cleanup can be retried on exit.
                slots[index] = MultiscreenSlot(channel, sessionId = prepared.sessionId)
                if (prepared.url.isEmpty()) {
                    release(index)
                    throw IllegalStateException("No playable stream returned")
                }
                if (closed || version != versions[index]) {
                    release(index)
                    return@enqueue
                }
                slots[index] = MultiscreenSlot(channel, TileSource(prepared.url), prepared.sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                slots[index] = slots[index].copy(channel = slots[index].channel ?: channel, loading = false, error = TUNE_FAILED)
            }
            if (version == versions[index]) requested[index] = null
            publish()
        }
    }

    /** The player of slot `index` failed; ignored once the slot has moved on to another source. */
    fun fail(index: Int, source: TileSource) {
        val slot = slots.getOrNull(index) ?: return
        if (slot.source !== source) return
        slots[index] = slot.copy(error = STREAM_FAILED)
        publish()
    }

    /** Empties slot `index`; its sound moves to the first screen still playing. */
    fun remove(index: Int): Deferred<Unit> {
        if (closed || !valid(index)) return done()
        ++versions[index]
        return enqueue {
            try {
                release(index)
                requested[index] = null
                if (audioIndex == index) audioIndex = maxOf(0, slots.indexOfFirst { it.source != null })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                slots[index] = slots[index].copy(error = RELEASE_FAILED)
            }
            publish()
        }
    }

    /**
     * Supersedes every pending tune and releases every session; throws if any release failed
     * (the released ones are empty, so calling it again retries only the rest).
     */
    suspend fun close() {
        closed = true
        versions.indices.forEach { versions[it]++ }
        enqueue {
            val failed = coroutineScope {
                slots.indices.map { index ->
                    async {
                        try {
                            release(index)
                            false
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            true
                        }
                    }
                }.awaitAll()
            }
            publish()
            if (failed.any { it }) throw IllegalStateException("Unable to release all multiscreen sessions")
        }.await()
    }

    private fun done(): Deferred<Unit> = CompletableDeferred(Unit)

    companion object {
        const val MAX_SCREENS = 4
        const val TUNE_FAILED = "Unable to tune. Retry or choose another channel."
        const val STREAM_FAILED = "Stream unavailable. Retry or choose another channel."
        const val RELEASE_FAILED = "Unable to release stream. Try again."
    }
}
