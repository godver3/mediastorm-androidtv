package com.strmr.tv.app

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.Profile
import com.strmr.tv.api.ServerVersion
import com.strmr.tv.api.login
import com.strmr.tv.api.logout
import com.strmr.tv.api.profiles
import com.strmr.tv.api.reportDisplayCapabilities
import com.strmr.tv.api.verifyPin
import com.strmr.tv.auth.DiscoveredServer
import com.strmr.tv.launcher.DeepLinkRouter
import com.strmr.tv.launcher.LauncherSync
import com.strmr.tv.remote.RemoteAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

/**
 * Startup and account state: which server, whether signed in, which profile. Drives
 * RootScreen; [ApiClient] does the transport. Main thread only. Its parts: [SessionPrefs]
 * (what's kept across launches), [RemoteSession] (iroh), [ProfileSetup] (what a profile
 * switch touches) and [ProfileUnlock] (PIN prompts mid-flow).
 */
object AppSession {
    sealed interface Phase {
        data object Starting : Phase
        data object Server : Phase
        data object Login : Phase
        data object Profiles : Phase
        data object Ready : Phase
        /** A stored session couldn't be checked (server unreachable); retryable. */
        data class Unavailable(val message: String) : Phase
    }

    /** A request hit a locked profile; RootScreen asks for its PIN. */
    data class PinChallenge(val profile: Profile)

    private val _phase = MutableStateFlow<Phase>(Phase.Starting)
    val phase: StateFlow<Phase> = _phase
    val serverUrl = MutableStateFlow<HttpUrl?>(null)
    val profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profile = MutableStateFlow<Profile?>(null)
    val serverVersion = MutableStateFlow<ServerVersion?>(null)
    /** Connected over iroh remote access (`serverUrl` is then the loopback proxy). */
    val isRemote: MutableStateFlow<Boolean> get() = RemoteSession.isRemote
    /** The server's custom logo (admin UI → Appearance & Branding), when one is uploaded. */
    val brandLogoUrl = MutableStateFlow<String?>(null)
    /** Shown on the login screen after an involuntary sign-out. */
    val loginNotice = MutableStateFlow<String?>(null)
    val pinChallenge: MutableStateFlow<PinChallenge?> get() = ProfileUnlock.pinChallenge
    /** Bumped each time the app comes to the foreground, so pages can refresh. */
    val wakes = MutableStateFlow(0)

    private val api = ApiClient
    private val scope = MainScope()
    private var started = false

    val username: String? get() = api.session?.username
    val lastProfileId: String? get() = SessionPrefs.lastProfileId
    /** "Use this profile automatically": opened at launch without the picker (or its PIN). */
    val autoProfileId: String? get() = SessionPrefs.autoProfileId
    /** Switching from Settings (rather than first pick) can be cancelled. */
    val canCancelProfilePick: Boolean get() = profile.value != null

    // Startup

    fun start(autoSignIn: Boolean) {
        if (started) return
        started = true
        api.onSignedOut = {
            scope.launch {
                LauncherSync.clear("session expired")
                loginNotice.value = "Your session expired. Sign in again."
                _phase.value = Phase.Login
            }
        }
        api.onProfileLocked = { id -> withContext(Dispatchers.Main) { unlockLockedProfile(id) } }
        api.recoverTransport = {
            withContext(Dispatchers.Main) {
                if (isRemote.value) runCatching { RemoteSession.reconnectOrThrow() }
                serverUrl.value
            }
        }
        ProfileSetup.watchDisplay(::reportDisplayCapabilities)
        scope.launch {
            val saved = SessionPrefs.server
            if (RemoteSession.paired) {
                RemoteSession.start()
            } else if (saved != null) {
                useServer(saved)
                if (api.session != null) resumeSession() else _phase.value = Phase.Login
            } else if (RemoteAccess.pairingUnreadable) {
                // Kept for a later launch; pairing again replaces it.
                _phase.value = Phase.Server
            } else {
                // Keystore values can outlive a data clear on some boxes; a token without a server is stale.
                ServerSetup.forgetEverything()
                _phase.value = Phase.Server
            }
            if (autoSignIn) DebugPrefill.autoSignIn()
        }
    }

    /** Re-checks a stored session by listing profiles, then restores the last one. */
    suspend fun resumeSession() {
        if (api.session == null) {
            _phase.value = Phase.Login
            return
        }
        _phase.value = Phase.Starting
        if (isRemote.value) {
            try {
                RemoteSession.reconnectOrThrow()
            } catch (e: Exception) {
                _phase.value = Phase.Unavailable(e.message ?: "Couldn't reach the server over remote access.")
                return
            }
        }
        try {
            loadProfiles()
        } catch (e: ApiError.SignedOut) {
            return // onSignedOut moved us to Login
        } catch (e: Exception) {
            _phase.value = Phase.Unavailable(e.message ?: "Couldn't reach the server.")
            return
        }
        val list = profiles.value
        val auto = list.firstOrNull { it.id == autoProfileId }
        // Opened from a home screen row: that row's profile, when it needs no PIN.
        val linked = DeepLinkRouter.pendingProfileId?.let { id -> list.firstOrNull { it.id == id && !it.requiresPin } }
        when {
            linked != null -> activate(linked)
            auto != null -> activate(auto)
            list.size == 1 && !list[0].requiresPin -> activate(list[0])
            else -> _phase.value = Phase.Profiles
        }
    }

    // Server ([ServerSetup])

    suspend fun connect(input: String) = ServerSetup.connect(input)

    suspend fun connect(discovered: DiscoveredServer) = ServerSetup.connect(discovered)

    internal fun moveTo(next: Phase) {
        _phase.value = next
    }

    internal fun useServer(url: HttpUrl) = ServerSetup.use(url, scope)

    /**
     * Try Again on the startup error: a remote launch that never connected starts over. In the
     * session's scope, since leaving the error screen would cancel the screen's own.
     */
    fun retryStartup() {
        scope.launch { if (isRemote.value && RemoteSession.paired) RemoteSession.start() else resumeSession() }
    }

    fun changeServer() {
        _phase.value = Phase.Server
    }

    /** Whether "Change Server" can be backed out of: a server is in use, or a pairing is saved. */
    val canCancelServerChange: Boolean get() = serverUrl.value != null || RemoteSession.paired

    /** Back out of "Change Server" to wherever we were. */
    fun cancelServerChange() {
        if (serverUrl.value == null) {
            // Paired, but the launch never connected: try it again.
            if (RemoteSession.paired) scope.launch { RemoteSession.start() }
            return
        }
        _phase.value = when {
            profile.value != null -> Phase.Ready
            api.session != null -> Phase.Profiles
            else -> Phase.Login
        }
    }

    // Remote access ([RemoteSession])

    suspend fun pair(code: String) = RemoteSession.pair(code)

    /** Waking: a remote connection's path can have died while the app slept. */
    fun reconnectRemote() {
        if (!isRemote.value) return
        scope.launch { runCatching { RemoteSession.reconnectOrThrow() } }
    }

    // Account

    suspend fun signIn(username: String, password: String) {
        loginNotice.value = null
        api.login(username, password)
        loadProfiles()
        val list = profiles.value
        if (list.size == 1 && !list[0].requiresPin) activate(list[0]) else _phase.value = Phase.Profiles
    }

    suspend fun signOut() {
        cancelChallenge()
        LauncherSync.clear("signed out")
        api.logout()
        profile.value = null
        profiles.value = emptyList()
        SessionPrefs.forgetProfiles()
        _phase.value = Phase.Login
    }

    // Profiles

    suspend fun loadProfiles() {
        val list = api.profiles()
        if (list.isEmpty()) throw ApiError.NotReady("This account has no profiles. Add one in the web UI.")
        profiles.value = list
    }

    /**
     * Picks a profile, checking its PIN first when it has one, and remembers it for launch
     * when [automatic]. Returns false for a wrong PIN.
     */
    suspend fun select(candidate: Profile, pin: String? = null, automatic: Boolean = false): Boolean {
        if (candidate.requiresPin && (pin.isNullOrEmpty() || !api.verifyPin(pin, candidate.id))) return false
        setAutoProfile(if (automatic) candidate.id else null)
        activate(candidate)
        return true
    }

    fun setAutoProfile(id: String?) = SessionPrefs.saveAutoProfile(id)

    suspend fun switchProfile() {
        // As on tvOS: the rows go while the picker is up; cancelling writes them again.
        LauncherSync.clear("switching profile")
        _phase.value = Phase.Profiles
        runCatching { loadProfiles() }
    }

    fun cancelProfilePick() {
        if (profile.value == null) return
        _phase.value = Phase.Ready
        LauncherSync.request("profile pick cancelled")
    }

    private fun activate(chosen: Profile) {
        ProfileSetup.beforeActivating(chosen, profile.value?.id)
        profile.value = chosen
        SessionPrefs.saveLastProfile(chosen.id)
        _phase.value = Phase.Ready
        ProfileSetup.afterActivating(chosen, scope)
    }

    /**
     * Adaptive playback's display report; needs the device registered, so only once a profile
     * is in use. Again on wake and whenever the display changes (another TV, a new output mode).
     */
    fun reportDisplayCapabilities() {
        if (_phase.value != Phase.Ready) return
        scope.launch { api.reportDisplayCapabilities() }
    }

    // Locked profile mid-flow

    /** Asks for the PIN over the current screen; on cancel, goes back to the profile picker. */
    private suspend fun unlockLockedProfile(id: String): Boolean {
        // No prompt before a profile is in use: the call fails.
        val locked = (profiles.value.firstOrNull { it.id == id } ?: profile.value?.takeIf { it.id == id })
            ?.takeIf { _phase.value == Phase.Ready }
        return ProfileUnlock.unlock(id, locked) {
            if (id == profile.value?.id && _phase.value == Phase.Ready) switchProfile()
        }
    }

    suspend fun verifyChallenge(pin: String): PinVerification = ProfileUnlock.verify(pin)

    fun cancelChallenge() = ProfileUnlock.cancel()
}
