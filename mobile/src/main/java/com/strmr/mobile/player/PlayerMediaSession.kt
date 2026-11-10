package com.strmr.mobile.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.strmr.tv.api.Artwork
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.PlayerController

/**
 * The playing session as the system sees it: the media notification, the lock screen and
 * Bluetooth / headset buttons, through Media3's [MediaSession]. The session is the activity's
 * (it owns the [PlayerController]); [PlaybackService] only publishes it, so the notification
 * keeps the process in the foreground while audio plays in the background.
 */
@OptIn(UnstableApi::class)
object PlayerMediaSession {
    var session: MediaSession? = null
        private set

    fun start(context: Context, controller: PlayerController) {
        stop(context)
        val activity = PendingIntent.getActivity(
            context, 0, Intent(context, MobilePlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val seekBack = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setDisplayName("Back 10 seconds").setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK).build()
        val seekForward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setDisplayName("Forward 10 seconds").setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD).build()
        session = MediaSession.Builder(context, SessionPlayer(controller))
            .setId("strmr-player")
            .setSessionActivity(activity)
            .setMediaButtonPreferences(if (controller.seekable) listOf(seekBack, seekForward) else emptyList())
            .build()
        runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
            .onFailure { AppLog.w("PlayerSession", "media service didn't start: ${it.message}") }
    }

    fun stop(context: Context) {
        val s = session ?: return
        session = null
        s.release()
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}

/**
 * The controller's player with the title for the notification and lock screen (the stream's
 * own metadata is a file name at best), and without seeking where the stream can't seek
 * (a live channel with no timeshift window).
 */
@OptIn(UnstableApi::class)
private class SessionPlayer(private val controller: PlayerController) : ForwardingPlayer(controller.player) {
    private val metadata: MediaMetadata = controller.request.let { r ->
        val art = r.episode?.imageUrl ?: r.backdrops.firstOrNull() ?: r.title.backdropUrl ?: r.title.posterUrl
        MediaMetadata.Builder()
            .setTitle(r.episode?.name?.takeIf { it.isNotBlank() } ?: r.title.name)
            .setArtist(if (r.episode != null) "${r.title.name} · ${r.episode!!.code}" else controller.session.sourceLabel)
            .setDisplayTitle(r.displayTitle)
            .apply { Artwork.url(art, "w780")?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
    }

    override fun getMediaMetadata(): MediaMetadata = metadata

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        if (controller.seekable) return commands
        return commands.buildUpon().removeAll(
            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT,
        ).build()
    }

    override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)
}

/**
 * Publishes [PlayerMediaSession.session]: Media3 posts the media notification for it and runs
 * this service in the foreground while it plays, so background audio isn't cut off.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    override fun onCreate() {
        super.onCreate()
        PlayerMediaSession.session?.let { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = PlayerMediaSession.session

    /** Swiped out of recents while paused: nothing to keep alive. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = PlayerMediaSession.session?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }
}
