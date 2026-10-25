package com.strmr.tv.update

import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.BuildConfig
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.await
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.PlaybackCoordinator
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Self-update for a sideloaded build: reads the release manifest ([UpdateManifest]) at launch,
 * on every wake and every six hours, downloads a newer APK in the background, checks its
 * SHA-256 and signing key, and installs it through [UpdateInstaller]. With Automatic Updates
 * On, a build that can install silently waits until the user leaves the app; one that needs
 * the system's confirmation (and Ask First) shows [com.strmr.tv.ui.settings.UpdatePrompt]
 * instead, never over the player. Debug builds only check from Settings.
 */
object AppUpdater {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val at: Instant) : State
        data class Available(val manifest: UpdateManifest) : State
        data class Downloading(val manifest: UpdateManifest, val percent: Int) : State
        data class Ready(val manifest: UpdateManifest, val apk: File) : State
        data class Installing(val manifest: UpdateManifest) : State
        data class Failed(val message: String, val manifest: UpdateManifest? = null) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set
    /** The in-app prompt is up (shown from the top page by UpdatePrompt). */
    var prompting by mutableStateOf(false)
        private set

    private const val INTERVAL_MS = 6 * 60 * 60 * 1000L
    private val scope = MainScope()
    private val dir by lazy { File(App.context.cacheDir, "updates") }
    private val http by lazy { ApiClient.http.newBuilder().readTimeout(30, TimeUnit.SECONDS).build() }
    private var lastCheck = 0L
    private var loop: Job? = null
    /** The system's confirmation screen, held until the app is on screen without the player. */
    private var confirmation: Intent? = null
    /** "Later" on this build: no prompt for it until the next launch or a manual check. */
    private var snoozed: Int? = null
    /** Install Now opened the permission switch; installing resumes when the app is back. */
    private var awaitingPermission = false

    /** From App.onCreate: clears downloads of builds now installed, starts the periodic check. */
    fun start() {
        dir.listFiles()?.filter { (it.nameWithoutExtension.toIntOrNull() ?: 0) <= BuildConfig.VERSION_CODE }?.forEach { it.delete() }
        AppForeground.onLeft = ::onLeftApp
        if (BuildConfig.DEBUG || loop != null) return
        loop = scope.launch {
            while (isActive) {
                checkIfDue()
                delay(INTERVAL_MS)
            }
        }
    }

    /**
     * MainActivity.onResume: a wake may be hours after the last check. Also the return from the
     * "Install unknown apps" switch, which on Android TV is a side panel that only pauses us.
     */
    fun onForeground() {
        if (!BuildConfig.DEBUG) scope.launch { checkIfDue() }
        if (PlaybackCoordinator.playerOpen) return
        if (awaitingPermission) {
            awaitingPermission = false
            if (UpdateInstaller.allowed) {
                install()
                return
            }
        }
        val ready = state as? State.Ready
        if (confirmation != null || (ready != null && ready.manifest.versionCode != snoozed && !installsOnLeave())) prompting = true
    }

    /** Automatic Updates On on a build that can install without the system's confirmation. */
    private fun installsOnLeave() =
        UpdateSettings.mode == UpdateSettings.Mode.Auto && UpdateInstaller.silent && UpdateInstaller.allowed

    private suspend fun checkIfDue() {
        if (UpdateSettings.mode == UpdateSettings.Mode.Off) return
        if (System.currentTimeMillis() - lastCheck < INTERVAL_MS - 60_000) return
        check(manual = false)
    }

    /** Settings' row: check, or act on what the last check found. */
    fun select() {
        when (val s = state) {
            is State.Checking, is State.Downloading, is State.Installing -> Unit
            is State.Available -> scope.launch { download(s.manifest, manual = true) }
            is State.Ready -> { snoozed = null; prompting = true }
            is State.Failed -> if (s.manifest != null) scope.launch { download(s.manifest, manual = true) } else scope.launch { check(manual = true) }
            else -> scope.launch { check(manual = true) }
        }
    }

    private suspend fun check(manual: Boolean) {
        if (state is State.Checking || state is State.Downloading || state is State.Installing) return
        if (manual) snoozed = null
        val previous = state
        state = State.Checking
        lastCheck = System.currentTimeMillis()
        val manifest = try {
            fetchManifest()
        } catch (e: Exception) {
            AppLog.w("Update", "check failed: ${e.message}")
            state = if (manual || previous !is State.Ready) State.Failed(e.message ?: "Couldn't check for updates") else previous
            return
        }
        if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
            state = State.UpToDate(Instant.now())
            return
        }
        if (previous is State.Ready && previous.manifest == manifest) {
            state = previous
            return
        }
        AppLog.i("Update", "${manifest.label} available (installed ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}))")
        state = State.Available(manifest)
        if (manual || UpdateSettings.mode != UpdateSettings.Mode.Off) download(manifest, manual)
    }

    private suspend fun fetchManifest(): UpdateManifest {
        // The query defeats any cache between here and the release CDN.
        val url = BuildConfig.UPDATE_MANIFEST_URL + "?t=" + System.currentTimeMillis() / 60_000
        val result = http.newCall(Request.Builder().url(url).build()).await()
        if (!result.isSuccessful) throw IOException("Update check failed (HTTP ${result.code})")
        return ApiClient.json.decodeFromString(UpdateManifest.serializer(), result.text)
    }

    private suspend fun download(manifest: UpdateManifest, manual: Boolean) {
        state = State.Downloading(manifest, 0)
        val apk = File(dir, "${manifest.versionCode}.apk")
        try {
            withContext(Dispatchers.IO) { fetch(manifest, apk) }
            val problem = withContext(Dispatchers.IO) {
                if (!UpdateInstaller.sha256(apk).equals(manifest.sha256, ignoreCase = true)) "The download was corrupted"
                else UpdateInstaller.problem(apk, manifest.versionCode)
            }
            if (problem != null) {
                apk.delete()
                throw IOException(problem)
            }
        } catch (e: Exception) {
            AppLog.w("Update", "download of ${manifest.label} failed: ${e.message}")
            state = State.Failed(e.message ?: "Download failed", manifest)
            return
        }
        withContext(Dispatchers.IO) { fetchProfile(manifest, UpdateInstaller.profileFor(apk)) }
        AppLog.i("Update", "${manifest.label} downloaded and verified")
        state = State.Ready(manifest, apk)
        val silentAuto = installsOnLeave() && !manual
        when {
            silentAuto && !AppForeground.visible -> install()
            silentAuto -> Unit // installs in onLeftApp
            manual || manifest.versionCode != snoozed -> if (AppForeground.visible && !PlaybackCoordinator.playerOpen) prompting = true
        }
    }

    private suspend fun fetch(manifest: UpdateManifest, apk: File) {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val response = http.newCall(Request.Builder().url(manifest.url).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("Download failed (HTTP ${it.code})")
            val body = it.body ?: throw IOException("Download failed (empty response)")
            val total = body.contentLength().takeIf { n -> n > 0 } ?: manifest.size
            var read = 0L
            var shown = -1
            body.byteStream().use { input ->
                apk.outputStream().use { out ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        val percent = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else 0
                        if (percent != shown) {
                            shown = percent
                            withContext(Dispatchers.Main) { state = State.Downloading(manifest, percent) }
                        }
                    }
                }
            }
        }
    }

    /**
     * The baseline profile for this Android version, beside the APK. Optional: without it the
     * update installs uncompiled and runs slowly until the system's idle compile.
     */
    private fun fetchProfile(manifest: UpdateManifest, file: File) {
        val sdk = android.os.Build.VERSION.SDK_INT
        val profile = manifest.profiles.firstOrNull { sdk in it.minApi..it.maxApi } ?: return
        runCatching {
            http.newCall(Request.Builder().url(profile.url).build()).execute().use {
                if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                file.outputStream().use { out -> it.body!!.byteStream().copyTo(out) }
            }
            if (!UpdateInstaller.sha256(file).equals(profile.sha256, ignoreCase = true)) throw IOException("checksum mismatch")
        }.onFailure {
            file.delete()
            AppLog.w("Update", "baseline profile for ${manifest.label} skipped: ${it.message}")
        }
    }

    /** The prompt's Install, or leaving the app with a silent update ready. */
    fun install() {
        prompting = false
        confirmation?.let {
            confirmation = null
            launchConfirmation(it)
            return
        }
        val ready = state as? State.Ready ?: return
        if (!UpdateInstaller.allowed && UpdateInstaller.requestPermission()) {
            awaitingPermission = true
            return
        }
        state = State.Installing(ready.manifest)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { UpdateInstaller.install(ready.apk) }
            } catch (e: Exception) {
                AppLog.e("Update", "install of ${ready.manifest.label} failed", e)
                state = State.Failed(e.message ?: "Install failed", ready.manifest)
            }
        }
    }

    /** The prompt's Later. */
    fun later() {
        prompting = false
        (state as? State.Ready)?.let { snoozed = it.manifest.versionCode }
    }

    private fun onLeftApp() {
        val ready = state as? State.Ready ?: return
        if (installsOnLeave()) {
            AppLog.i("Update", "app left; installing ${ready.manifest.label}")
            install()
        }
    }

    /** From the receiver: the system wants its confirmation screen shown. */
    fun confirmationNeeded(intent: Intent) {
        scope.launch {
            if (AppForeground.visible && !PlaybackCoordinator.playerOpen) {
                launchConfirmation(intent)
            } else {
                confirmation = intent
            }
        }
    }

    private fun launchConfirmation(intent: Intent) {
        runCatching { App.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { installFailed(PackageInstaller.STATUS_FAILURE, it.message) }
    }

    /** From the receiver: cancelled on the confirmation screen, or the system refused it. */
    fun installFailed(status: Int, message: String?) {
        scope.launch {
            val manifest = (state as? State.Installing)?.manifest ?: (state as? State.Ready)?.manifest
            val apk = manifest?.let { File(dir, "${it.versionCode}.apk") }
            AppLog.w("Update", "install ended: status=$status $message")
            state = when {
                status == PackageInstaller.STATUS_FAILURE_ABORTED && manifest != null && apk?.exists() == true -> {
                    snoozed = manifest.versionCode
                    State.Ready(manifest, apk)
                }
                else -> State.Failed(message ?: "Install failed", manifest)
            }
        }
    }
}
