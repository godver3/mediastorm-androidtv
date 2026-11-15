package com.strmr.tv.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only active rotations for the same authentication context may share a result. */
internal class ScopedRefresh<T>(private val scope: CoroutineScope) {
    private data class Pending<T>(val version: Long, val job: Deferred<T>)
    private val lock = Mutex()
    private var pending: Pending<T>? = null

    suspend fun run(context: RequestContext.Scope, rotate: suspend () -> T): T {
        context.check()
        val job = lock.withLock {
            context.check()
            pending?.takeIf { it.version == context.authenticationVersion && !it.job.isCompleted }?.job
                ?: scope.async { context.check(); rotate() }.also { pending = Pending(context.authenticationVersion, it) }
        }
        return try {
            val result = job.await()
            context.check()
            result
        } finally {
            // A cancelled waiter must not detach a still-running shared rotation.
            if (job.isCompleted) lock.withLock { if (pending?.job === job) pending = null }
        }
    }
}
