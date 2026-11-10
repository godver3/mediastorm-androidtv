package com.strmr.mobile.cast

import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import com.google.android.gms.cast.CastDevice as GmsCastDevice

/**
 * Google's Cast SDK: Chromecasts able to run the Default Media Receiver, found through
 * MediaRouter while a picker is open (an active scan; passive otherwise), and the sender
 * session to one of them. Main thread only, as the SDK requires.
 */
object GoogleCastService {
    private const val TAG = "Cast"
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    /** Chromecasts on the network, as MediaRouter lists them. */
    val devices: StateFlow<List<CastDevice>> = _devices
    /** Called when the session ends from the receiver's side (another sender took it, TV off). */
    var onSessionEnded: ((CastSession) -> Unit)? = null

    private var context: CastContext? = null
    private var pendingStart: CancellableContinuation<CastSession>? = null
    /** The Chromecast [pendingStart] waits on: the end of a session elsewhere (switching TVs) isn't its failure. */
    private var pendingDeviceId: String? = null
    private val selector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }
    private val router by lazy { MediaRouter.getInstance(App.context) }

    private val routes = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    private val sessions = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = started(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = started(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = failed("couldn't start ($error)")
        override fun onSessionResumeFailed(session: CastSession, error: Int) = failed("couldn't resume ($error)")
        override fun onSessionEnded(session: CastSession, error: Int) {
            AppLog.i(TAG, "session ended ($error)")
            if (session.castDevice?.deviceId == pendingDeviceId) failed("ended ($error)")
            onSessionEnded?.invoke(session)
        }
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    private fun started(session: CastSession) {
        AppLog.i(TAG, "session started on ${session.castDevice?.friendlyName}")
        if (pendingDeviceId != null && session.castDevice?.deviceId != pendingDeviceId) return
        pendingStart?.takeIf { it.isActive }?.resume(session)
        pendingStart = null
    }

    private fun failed(reason: String) {
        pendingStart?.takeIf { it.isActive }?.resumeWithException(CastException("The Chromecast $reason."))
        pendingStart = null
    }

    /**
     * The Cast context, made once; null where Google Play services can't provide it (a phone
     * without them), and Chromecasts are simply never listed.
     */
    private suspend fun castContext(): CastContext? {
        context?.let { return it }
        val made = runCatching {
            suspendCancellableCoroutine { cont ->
                CastContext.getSharedInstance(App.context, Executors.newSingleThreadExecutor())
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        }.onFailure { AppLog.w(TAG, "Cast unavailable: ${it.message}") }.getOrNull() ?: return null
        made.sessionManager.addSessionManagerListener(sessions, CastSession::class.java)
        context = made
        return made
    }

    /** The picker is up: an active scan, the list refreshed as routes come and go. */
    suspend fun startDiscovery() {
        castContext() ?: return
        router.addCallback(selector, routes, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
    }

    /** A passive scan keeps the list warm without the active mDNS traffic. */
    fun stopDiscovery() {
        if (context == null) return
        router.addCallback(selector, routes, 0)
    }

    private fun castRoutes() = router.routes.filter { !it.isDefaultOrBluetooth && it.isEnabled && it.matchesSelector(selector) }

    private fun refresh() {
        _devices.value = castRoutes().map { route ->
            val gms = GmsCastDevice.getFromBundle(route.extras)
            CastDevice(
                id = "cast:${route.id}", name = route.name, model = gms?.modelName, kind = CastDevice.Kind.GOOGLE_CAST,
                address = runCatching { gms?.inetAddress?.hostAddress }.getOrNull(),
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** Connects to [device] (launching, or joining, the Default Media Receiver). */
    suspend fun connect(device: CastDevice): GoogleCastTransport {
        val cast = castContext() ?: throw CastException("Casting needs Google Play services on this device.")
        val route = castRoutes().firstOrNull { "cast:${it.id}" == device.id }
            ?: throw CastException("${device.name} is no longer on the network.")
        val wanted = GmsCastDevice.getFromBundle(route.extras)?.deviceId
        cast.sessionManager.currentCastSession?.let { current ->
            if (current.isConnected && current.castDevice?.deviceId == wanted && current.remoteMediaClient != null) {
                return GoogleCastTransport(device, current)
            }
        }
        pendingStart?.takeIf { it.isActive }?.cancel()
        val session = withTimeout(30_000) {
            suspendCancellableCoroutine { cont ->
                pendingStart = cont
                pendingDeviceId = wanted
                cont.invokeOnCancellation { if (pendingStart === cont) pendingStart = null }
                router.selectRoute(route)
            }
        }
        if (session.remoteMediaClient == null) throw CastException("Couldn't connect to ${device.name}.")
        return GoogleCastTransport(device, session)
    }

    /** Ends the session and stops the receiver app. */
    fun disconnect() {
        context?.sessionManager?.endCurrentSession(true)
    }
}
