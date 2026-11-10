package com.strmr.mobile.cast

import com.strmr.tv.api.SelectedTrack
import com.strmr.tv.api.TrackSelection
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.player.PlaybackRequest
import com.strmr.tv.player.PlayerController
import com.strmr.tv.player.PlayerSession
import com.strmr.tv.player.ResolvedStream
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The two ways a cast starts, as in the Expo and iPhone apps: Cast on the details page plays
 * the title (through the same prequeue / library resolve as Play) on the device instead of the
 * phone, and the player's Cast button hands what is playing over at the current position.
 */

/** The device armed by the details page, when, and the watch that disarms it. */
private class Handoff(val device: CastDevice, val armedAt: Long, val watch: Job)

private var handoff: Handoff? = null

/** An armed cast takes the next resolve only this long (Play may have opened a Versions list that was closed). */
private const val ARMED_MS = 60_000L

/** Once at start-up: resolved streams are offered to an armed cast before the player opens. */
fun CastController.install() {
    PlaybackCoordinator.streamClaim = { request, resolved -> claim(request, resolved) }
}

/**
 * Details page: the picker chose [device]; [play] then starts the usual resolve, whose stream
 * [claim] takes. Disarmed if that resolve ends without a stream (cancelled, failed).
 */
fun CastController.arm(device: CastDevice, play: () -> Unit) {
    disarm()
    // Undispatched, so it sees the resolve that [play] starts.
    val watch = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        PlaybackCoordinator.resolving.drop(1).collect { if (it == null) disarm() }
    }
    handoff = Handoff(device, System.currentTimeMillis(), watch)
    play()
}

private fun disarm() {
    handoff?.watch?.cancel()
    handoff = null
}

/** `PlaybackCoordinator` resolved a stream: true when an armed cast takes it. */
private fun CastController.claim(request: PlaybackRequest, resolved: ResolvedStream): Boolean {
    val armed = handoff ?: return false
    disarm()
    if (System.currentTimeMillis() - armed.armedAt > ARMED_MS) return false
    val subtitle = castSubtitleTrack(resolved.tracks)
    start(armed.device, request, resolved.path, request.startPosition ?: 0.0, audio = resolved.tracks?.audio?.streamIndex,
          subtitle = subtitle?.streamIndex, subtitleLanguage = subtitle?.let { it.languageCode ?: it.language })
    return true
}

/**
 * The subtitle a receiver can show: a text track only (a receiver shows a WebVTT rendition, not
 * a bitmap one), none when the profile has subtitles off.
 */
internal fun castSubtitleTrack(tracks: TrackSelection?): SelectedTrack? {
    if (tracks == null || tracks.subtitleMode == "off") return null
    return tracks.subtitle?.takeIf { it.bitmap != true } ?: tracks.textSubtitle
}

/** Whether the player's session can go to a TV: a title stream, or a live channel. */
fun PlayerSession.castable(): Boolean = (streamPath != null && reportsProgress) || (isLive && liveChannel != null)

/**
 * The player's Cast button chose [device]: pauses here, saves the position, closes the player
 * ([close]) and continues on [device] from there once the player has let go (a live channel's
 * provider slot is free by then).
 */
fun CastController.handOff(controller: PlayerController, device: CastDevice, close: () -> Unit) {
    val session = controller.session
    val position = controller.player.currentPosition / 1000.0
    val channel = session.liveChannel
    AppLog.i(CastController.TAG, "handing off to ${device.name} at ${position.toInt()}s")
    controller.player.pause()
    if (!session.isLive) controller.report(paused = true)
    close()
    scope.launch {
        withTimeoutOrNull(5_000) { while (PlaybackCoordinator.playerOpen) delay(100) }
        if (session.isLive && channel != null) {
            val program = session.request.title.name.takeIf { it != channel.name }
            startLive(device, channel, program)
        } else {
            val path = session.streamPath ?: return@launch
            val subtitle = castSubtitleTrack(session.tracks)
            start(device, session.request, path, position, audio = session.tracks?.audio?.streamIndex,
                  subtitle = subtitle?.streamIndex, subtitleLanguage = subtitle?.let { it.languageCode ?: it.language })
        }
    }
}
