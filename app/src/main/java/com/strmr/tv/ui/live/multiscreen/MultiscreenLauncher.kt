package com.strmr.tv.ui.live.multiscreen

import android.content.Intent
import com.strmr.tv.app.App
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStream
import com.strmr.tv.live.SavedMultiscreen

/**
 * The full-screen live player's channel, for the first screen. [stream] is the player's HLS
 * session, handed over as is (the multiscreen keeps and stops it, without a second provider
 * connection); null when the player was direct, so the first screen tunes the channel again.
 */
data class MultiscreenHandover(val channel: LiveChannel, val stream: LiveStream?)

/**
 * Opens [MultiscreenActivity], from the Live tab (pick two channels, or [resume] the last
 * screens) or the live player ([MultiscreenHandover]).
 */
object MultiscreenLauncher {
    /** What the activity starts with; taken once as it opens. */
    @Volatile private var pending: MultiscreenHandover? = null
    @Volatile private var pendingResume: SavedMultiscreen? = null

    fun open(handover: MultiscreenHandover? = null) {
        pending = handover
        pendingResume = null
        start()
    }

    /** Reopens a saved multiscreen, without the picker. */
    fun resume(saved: SavedMultiscreen) {
        pending = null
        pendingResume = saved
        start()
    }

    private fun start() {
        App.context.startActivity(Intent(App.context, MultiscreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal fun take(): Pair<MultiscreenHandover?, SavedMultiscreen?> = (pending to pendingResume).also {
        pending = null
        pendingResume = null
    }
}
