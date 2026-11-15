package com.strmr.tv.library

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** An ephemeral boundary, never a credential or a persisted cache key. */
object ProfileDataContext {
    private val epoch = AtomicLong()
    val current: Long get() = epoch.get()
    fun invalidate() { epoch.incrementAndGet() }
    fun requireCurrent(captured: Long) {
        if (captured != current) throw CancellationException("Profile data context changed")
    }
}

/** Main-thread request sharing. Reset detaches old work without blocking a new profile. */
internal class ProfileRefresh(private val context: () -> Long = { ProfileDataContext.current }) {
    private class Flight(val context: Long, val generation: Long) {
        val done = CompletableDeferred<Unit>()
    }
    private var generation = 0L
    private var active: Flight? = null

    fun reset() { generation++; active = null }

    suspend fun <T> run(load: suspend () -> T, success: (T) -> Unit, failure: (Exception) -> Unit = {}, finish: () -> Unit = {}) {
        val captured = context()
        active?.takeIf { it.context == captured && it.generation == generation }?.let { return it.done.await() }
        val flight = Flight(captured, generation)
        active = flight
        fun current() = context() == flight.context && generation == flight.generation
        var completed = false
        try {
            val loaded = load()
            completed = true
            if (current()) success(loaded)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            completed = true
            if (current()) failure(error)
        } finally {
            if (completed && current()) finish()
            if (active === flight) active = null
            flight.done.complete(Unit)
        }
    }
}

internal suspend fun <T> optionalProfileRead(load: suspend () -> T): T? = try {
    load()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}
