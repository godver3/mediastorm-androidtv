package com.strmr.mobile.connection

import com.strmr.mobile.downloads.OfflineProgress
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.NetworkRoutes
import com.strmr.tv.update.AppForeground
import java.io.IOException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the phone connected (the iPhone app's `MobileConnection`, as the Expo app's
 * `BackendSettingsContext`): re-selects the route when the network changes (debounced), on
 * return to the foreground and every 30 s while a home network could take over; notices a
 * server that stopped answering (a transport failure, confirmed by `/health`); and retries
 * with backoff until it answers again. A device outage pauses retrying until the network
 * returns. The shell shows [status]: an offline screen before sign-in completes, a banner after.
 * Main thread only.
 */
object ConnectionManager {
    sealed interface Status {
        data object Online : Status
        /** No network on the device. */
        data object Offline : Status
        /** The server didn't answer; [nextRetryAt] is when the next attempt starts (null: now). */
        data class Reconnecting(val attempt: Int, val nextRetryAt: Long?) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Online)
    val status: StateFlow<Status> = _status
    /** Set for a few seconds after a recovery, for the banner's "Back Online". */
    val recoveredAt = MutableStateFlow<Long?>(null)
    /** Downloads shown over the app while the server is out of reach. */
    val showsOfflineDownloads = MutableStateFlow(false)

    private val scope = MainScope()
    private var retryJob: Job? = null
    private var changeJob: Job? = null
    private var checking: Deferred<Boolean>? = null
    private var backgroundedAt: Long? = null
    private var started = false
    private val selector = ConnectionSelector

    fun start() {
        if (started) return
        started = true
        NetworkMonitor.onChange = ::networkChanged
        NetworkMonitor.start()
        ApiClient.onTransportFailure = { e -> scope.launch { transportFailed(e) } }
        if (NetworkMonitor.isDeviceOffline.value) _status.value = Status.Offline
        scope.launch { AppSession.phase.collect(::phaseChanged) }
        scope.launch {
            while (isActive) {
                delay(30_000)
                periodicCheck()
            }
        }
    }

    /** Wired to [AppForeground] by the application. */
    fun backgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun foregrounded() {
        val away = backgroundedAt?.let { System.currentTimeMillis() - it } ?: 0
        backgroundedAt = null
        if (away < 1_000) return
        ReconnectJournal.record("app-foreground", mapOf("offline" to "${NetworkMonitor.isDeviceOffline.value}"))
        if (NetworkMonitor.isDeviceOffline.value) return goOffline()
        scope.launch {
            selector.select("app active")
            verify("app active")
            selector.refreshSettings()
        }
    }

    private fun phaseChanged(phase: AppSession.Phase) {
        when (phase) {
            is AppSession.Phase.Unavailable -> {
                ReconnectJournal.record("startup-unavailable")
                when {
                    NetworkMonitor.isDeviceOffline.value -> _status.value = Status.Offline
                    // A retry in progress keeps its backoff (it started this startup attempt).
                    _status.value !is Status.Reconnecting -> scheduleRetry(0)
                }
            }
            AppSession.Phase.Ready -> scope.launch {
                if (_status.value is Status.Reconnecting) recovered()
                selector.refreshSettings()
                selector.select("startup")
                OfflineProgress.flush()
            }
            else -> Unit
        }
    }

    /** Try Again, the banner's Retry, or Reconnect in settings: an attempt now. */
    fun retryNow() {
        if (NetworkMonitor.isDeviceOffline.value) return goOffline()
        val attempt = (_status.value as? Status.Reconnecting)?.attempt ?: 0
        retryJob?.cancel()
        retryJob = scope.launch { attemptReconnect(attempt) }
    }

    /** Check Connection in settings: one health check now; a failure starts the retry loop. */
    suspend fun checkNow(): Boolean {
        val ok = check()
        if (ok) {
            if (_status.value != Status.Online) recovered()
        } else if (_status.value == Status.Online && (isReady || isUnavailable)) {
            scheduleRetry(0)
        }
        return ok
    }

    // Events

    private fun networkChanged(path: NetworkMonitor.Path) {
        changeJob?.cancel()
        if (!path.satisfied) {
            // Wait for the two-second confirmation before calling it an outage.
            changeJob = scope.launch {
                delay(2_100)
                if (NetworkMonitor.isDeviceOffline.value) goOffline()
            }
            return
        }
        changeJob = scope.launch {
            delay(500) // coalesce the handover burst
            if (!AppForeground.visible && !isReady) return@launch
            ReconnectJournal.record("network-change", mapOf("interface" to path.iface.name.lowercase()))
            selector.select("network change")
            verify("network change")
        }
    }

    private fun periodicCheck() {
        if (!AppForeground.visible || !isReady || _status.value != Status.Online) return
        if (selector.automaticHome == null && !NetworkRoutes.isHomeDirect.value) return
        val before = AppSession.serverUrl.value
        scope.launch {
            selector.select("periodic")
            if (AppSession.serverUrl.value != before) verify("periodic route change")
        }
    }

    /** A request found no server. Checked against `/health` first, so one dropped request isn't an outage. */
    private fun transportFailed(e: IOException) {
        if (e.message?.contains("Canceled", ignoreCase = true) == true) return
        if (!isReady || _status.value != Status.Online) return
        scope.launch { verify("network failure") }
    }

    // Checks

    private val isReady get() = AppSession.phase.value == AppSession.Phase.Ready
    private val isUnavailable get() = AppSession.phase.value is AppSession.Phase.Unavailable

    private suspend fun verify(reason: String) {
        if (!isReady && !isUnavailable) return
        if (isUnavailable) {
            if (_status.value !is Status.Reconnecting) scheduleRetry(0)
            return
        }
        if (check()) {
            if (_status.value != Status.Online) recovered()
        } else if (_status.value !is Status.Reconnecting) {
            ReconnectJournal.record("server-unreachable", mapOf("reason" to reason))
            scheduleRetry(0)
        }
    }

    /** One health check, shared by concurrent callers. */
    private suspend fun check(): Boolean {
        checking?.let { return it.await() }
        val job = scope.async {
            if (NetworkMonitor.isDeviceOffline.value) return@async false
            if (AppSession.isRemote.value && runCatching { NetworkRoutes.reconnectTunnel() }.isFailure) return@async false
            val base = AppSession.serverUrl.value ?: return@async false
            IdentityProbe.health(base)
        }
        checking = job
        return try {
            job.await()
        } finally {
            if (checking === job) checking = null
        }
    }

    private fun scheduleRetry(attempt: Int) {
        val delayMs = RouteRules.backoffMs(attempt)
        _status.value = Status.Reconnecting(attempt, System.currentTimeMillis() + delayMs)
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            attemptReconnect(attempt)
        }
    }

    private suspend fun attemptReconnect(attempt: Int) {
        if (NetworkMonitor.isDeviceOffline.value) return goOffline()
        _status.value = Status.Reconnecting(attempt, null)
        ReconnectJournal.record("retry", mapOf("attempt" to "${attempt + 1}"))
        selector.select("retry")
        val ok = check()
        when {
            ok && isUnavailable -> {
                AppSession.retryStartup()
                // The startup retry runs in the session's scope; its outcome arrives as a phase.
                delay(RouteRules.backoffMs(attempt))
                if (isUnavailable) scheduleRetry(attempt + 1)
            }
            ok -> recovered()
            else -> scheduleRetry(attempt + 1)
        }
    }

    private fun goOffline() {
        retryJob?.cancel()
        retryJob = null
        if (_status.value == Status.Offline) return
        _status.value = Status.Offline
        ReconnectJournal.record("offline")
    }

    private fun recovered() {
        retryJob?.cancel()
        retryJob = null
        val wasDown = _status.value != Status.Online
        _status.value = Status.Online
        if (!wasDown) return
        ReconnectJournal.record("reconnected")
        val at = System.currentTimeMillis()
        recoveredAt.value = at
        AppSession.wakes.value += 1 // pages that failed while the server was away load again
        scope.launch {
            OfflineProgress.flush()
            delay(3_000)
            if (recoveredAt.value == at) recoveredAt.value = null
        }
    }
}
