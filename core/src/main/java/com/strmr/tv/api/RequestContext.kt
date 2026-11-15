package com.strmr.tv.api

import com.strmr.tv.library.ProfileDataContext
import kotlinx.coroutines.CancellationException

/** Serializes identity changes with request snapshots; credentials never form a key. */
internal class RequestContext(
    private val profileEpoch: () -> Long = { ProfileDataContext.current },
    private val invalidateProfile: () -> Unit = { ProfileDataContext.invalidate() },
) {
    private val lock = Any()
    private var authenticationVersion = 0L

    fun <T> serialized(block: () -> T): T = synchronized(lock, block)

    fun changeAuthentication(change: () -> Boolean, afterChange: () -> Unit = {}) = synchronized(lock) {
        if (change()) {
            authenticationVersion++
            invalidateProfile()
            afterChange()
        }
    }

    fun changeProfile(change: () -> Unit) = synchronized(lock) {
        try { change() } finally { invalidateProfile() }
    }

    fun capture(expectedProfile: Long? = null): Scope = synchronized(lock) {
        val current = profileEpoch()
        if (expectedProfile != null && expectedProfile != current) changed()
        Scope(this, authenticationVersion, current)
    }

    /** Refresh belongs to the account/server, so a profile switch need not discard rotation. */
    fun captureAuthentication(expectedVersion: Long? = null): Scope = synchronized(lock) {
        if (expectedVersion != null && expectedVersion != authenticationVersion) changed()
        Scope(this, authenticationVersion, null)
    }

    private fun <T> use(scope: Scope, block: () -> T): T = synchronized(lock) {
        if (scope.authenticationVersion != authenticationVersion ||
            scope.profileEpoch?.let { it != profileEpoch() } == true) changed()
        block()
    }

    internal class Scope internal constructor(
        private val owner: RequestContext,
        internal val authenticationVersion: Long,
        internal val profileEpoch: Long?,
    ) {
        fun check() { use {} }
        fun <T> use(block: () -> T): T = owner.use(this, block)
    }

    private fun changed(): Nothing = throw CancellationException("Request context changed")
}
