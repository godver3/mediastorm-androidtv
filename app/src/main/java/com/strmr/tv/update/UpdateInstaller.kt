package com.strmr.tv.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import java.io.File
import java.security.MessageDigest

/**
 * Hands a verified APK to [PackageInstaller]. Android never installs an app silently for a
 * sideloading client, with one exception it can use: from Android 12, an app that installed
 * the current build itself (its own installer of record) may update itself without a prompt.
 * The first self-update after an adb or Downloader install therefore asks; later ones on
 * Android 12+ need no prompt. Fire OS 7 and 8 (Android 9 and 11) always ask.
 */
object UpdateInstaller {
    private val context get() = App.context

    /** Whether the system will install without a confirmation screen. */
    val silent: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }
                .getOrNull() == context.packageName

    /** Whether the user allowed this app to install apps ("Install unknown apps"). */
    val allowed: Boolean get() = context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for this app; false where the TV has no such screen. */
    fun requestPermission(): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /** SHA-256 of the file, lowercase hex. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().hex()
    }

    /**
     * Why the APK can't replace this install (another package, not newer, another signing key),
     * else null; checked before the installer would fail with a bare "App not installed".
     */
    fun problem(apk: File, versionCode: Int): String? {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return "The download isn't a valid app"
        if (archive.packageName != context.packageName) return "The download is another app (${archive.packageName})"
        if (archive.longVersionCode < versionCode) return "The download is an older build"
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (signers(archive).intersect(signers(installed)).isEmpty()) {
            return "The update is signed with a different key; reinstall the app from the release page"
        }
        return null
    }

    /** SHA-256 of each signing certificate. */
    private fun signers(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
    }

    /** Writes the APK into an install session and commits it; the outcome arrives in [UpdateInstallReceiver]. */
    @SuppressLint("RequestInstallPackagesPolicy")
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 1 shl 16) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java)
                // Mutable: the installer adds the status and, if needed, the confirmation intent.
                val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
            AppLog.i("Update", "install session $id committed (silent=$silent)")
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}

/** The install session's outcome: a confirmation to show, or a failure (success replaces the process). */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { AppUpdater.confirmationNeeded(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> AppLog.i("Update", "installed")
            else -> AppUpdater.installFailed(status, message)
        }
    }
}
