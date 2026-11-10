package com.strmr.mobile.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.strmr.mobile.player.ui.pipAspect
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.PlayerController

/**
 * Picture in picture for the phone player: the window keeps the video's aspect, offers
 * −10 s / play-pause / +10 s, and (Android 12+) enters by itself when the viewer leaves the
 * app while it plays; below 12 the activity enters from `onUserLeaveHint`. The actions come
 * back as broadcasts to a receiver registered for the activity's life.
 */
class PictureInPicture(private val activity: ComponentActivity, private val controller: PlayerController) {
    val supported: Boolean = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_CONTROL, 0)) {
                CONTROL_PLAY -> controller.player.play()
                CONTROL_PAUSE -> controller.player.pause()
                CONTROL_BACK -> controller.skip(-10_000)
                CONTROL_FORWARD -> controller.skip(10_000)
            }
            update()
        }
    }

    fun register() {
        if (!supported) return
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        update()
    }

    fun unregister() {
        if (supported) runCatching { activity.unregisterReceiver(receiver) }
    }

    /** Refreshes the window's aspect, actions and auto-enter (the play state or the video changed). */
    fun update() {
        if (!supported) return
        runCatching { activity.setPictureInPictureParams(params()) }
            .onFailure { AppLog.w("PiP", "params refused: ${it.message}") }
    }

    /** Enters now (the PiP button, or leaving the app below Android 12); false when it can't. */
    fun enter(): Boolean {
        if (!supported) return false
        return runCatching { activity.enterPictureInPictureMode(params()) }
            .onFailure { AppLog.w("PiP", "enter failed: ${it.message}") }
            .getOrDefault(false)
    }

    private fun params(): PictureInPictureParams {
        val (w, h) = pipAspect(controller.videoAspect.value)
        val playing = controller.player.playWhenReady
        val actions = buildList {
            if (controller.seekable) add(action(CONTROL_BACK, android.R.drawable.ic_media_rew, "Back 10 seconds"))
            add(
                if (playing) action(CONTROL_PAUSE, android.R.drawable.ic_media_pause, "Pause")
                else action(CONTROL_PLAY, android.R.drawable.ic_media_play, "Play"),
            )
            if (controller.seekable) add(action(CONTROL_FORWARD, android.R.drawable.ic_media_ff, "Forward 10 seconds"))
        }
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(w, h))
            .setActions(actions)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Only while it plays: a paused player left behind just goes to the background.
                    setAutoEnterEnabled(playing && controller.error.value == null)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    private fun action(control: Int, icon: Int, title: String): RemoteAction {
        val intent = Intent(ACTION).setPackage(activity.packageName).putExtra(EXTRA_CONTROL, control)
        val pending = PendingIntent.getBroadcast(activity, control, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return RemoteAction(Icon.createWithResource(activity, icon), title, title, pending)
    }

    private companion object {
        const val ACTION = "com.strmr.mobile.PIP_CONTROL"
        const val EXTRA_CONTROL = "control"
        const val CONTROL_PLAY = 1
        const val CONTROL_PAUSE = 2
        const val CONTROL_BACK = 3
        const val CONTROL_FORWARD = 4
    }
}
