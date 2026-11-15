package com.strmr.tv.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One native proxy may change peers; never share its opening across identities. */
internal class ScopedConnection<T>(private val scope: CoroutineScope) {
    private data class Pending<T>(val identity: Any,val job: Deferred<T>)
    private val lock=Mutex()
    @Volatile private var pending:Pending<T>?=null
    private fun requireCurrent(check:()->Boolean){if(!check())throw CancellationException("Connection context changed")}

    suspend fun connect(identity:Any,isCurrent:()->Boolean,open:suspend()->T):T {
        requireCurrent(isCurrent)
        val job=lock.withLock {
            requireCurrent(isCurrent)
            if(pending?.identity!==identity){pending?.job?.cancelAndJoin();pending=null;requireCurrent(isCurrent)}
            pending?.takeIf{!it.job.isCompleted}?.job ?: scope.async {
                requireCurrent(isCurrent);val result=open();requireCurrent(isCurrent);result
            }.also{pending=Pending(identity,it)}
        }
        return try {
            val result=job.await();requireCurrent(isCurrent);result
        }finally{
            if(job.isCompleted)lock.withLock{if(pending?.job===job)pending=null}
        }
    }

    fun cancelActive() { pending?.job?.cancel() }

    /** Pairing changes the same native resource, so it waits for the old open to stop. */
    suspend fun <R> replace(operation:suspend()->R):R = lock.withLock {
        pending?.job?.cancelAndJoin();pending=null
        operation()
    }
}
