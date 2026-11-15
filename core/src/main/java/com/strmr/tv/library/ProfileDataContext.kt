package com.strmr.tv.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** An ephemeral boundary, never a credential or a persisted cache key. */
object ProfileDataContext {
    private val epoch = MutableStateFlow(0L)
    val changes: StateFlow<Long> = epoch.asStateFlow()
    val current: Long get() = epoch.value
    fun invalidate() { epoch.update { it + 1 } }
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
