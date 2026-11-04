package com.strmr.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.EpgProgram
import com.strmr.tv.live.LiveCatchups
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.catchupStreamUrl
import com.strmr.tv.live.epgSchedule
import com.strmr.tv.live.liveCatchup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A live channel with provider catch-up: the bar spans the programme airing at the playhead
 * ([timeline]), and a seek before what the player holds (the timeshift window, or nothing on a
 * direct tune) plays the provider's archive from that point instead ([CatchupRules.decide]).
 * The channel's live session is stopped meanwhile ([LiveRetune.park]), as a provider may allow
 * one connection. An archive runs up to what had aired when it was asked for, so at its end a
 * fresh one carries on from there, behind live by as much as before, past the programme's end
 * into the next; Go Live (or catching up with the edge) tunes the channel again.
 */
@OptIn(UnstableApi::class)
class CatchupJoin(private val controller: PlayerController, private val channel: LiveChannel) {
    private val player get() = controller.player
    private var programs: List<EpgProgram> = emptyList()
    private var archive: Archive? = null
    private var request: Job? = null
    private val archiveWindow = LiveCatchups.window(channel)

    /** The bar: the programme at the playhead; null until the guide has it. */
    val timeline = MutableStateFlow<ProgrammeTimeline?>(null)
    /** The provider's archive is playing in place of the channel. */
    val inArchive = MutableStateFlow(false)

    /** An archive playing: the programme it's for and where it starts, in wall-clock ms. */
    private data class Archive(val program: EpgProgram, val startWallMs: Long, val durationMs: Long, val firstPositionMs: Long)

    private val schedule = controller.scope.launch {
        while (isActive) {
            runCatching { loadSchedule() }.onFailure { if (it is CancellationException) throw it; AppLog.w("Catchup", "schedule: ${it.message}") }
            delay(SCHEDULE_REFRESH_MS)
        }
    }

    private suspend fun loadSchedule() {
        val back = (archiveWindow / 60_000).coerceAtMost(SCHEDULE_BACK_MINUTES).toInt()
        val list = ApiClient.epgSchedule(listOf(channel.guideKey), -back, (back / 60) + 4)[channel.guideKey].orEmpty()
        programs = list.filter { it.startMs > 0 && it.stopMs > it.startMs }.sortedBy { it.startMs }
    }

    /** The playhead in wall-clock ms: the archive's point, or now less how far behind live the channel plays. */
    fun playheadWallMs(now: Long = System.currentTimeMillis()): Long =
        archive?.let { it.startWallMs + player.currentPosition } ?: (now - liveBehindMs())

    private fun liveBehindMs(): Long = if (controller.timeshift.value) Timeshift.behindLiveMs(player) else 0

    /** Ticked by the controller's clock: the bar, and how far behind live the archive plays. */
    fun tick(now: Long = System.currentTimeMillis()) {
        val wall = playheadWallMs(now)
        val program = CatchupRules.programAt(programs, wall)
        timeline.value = program?.let { CatchupRules.timeline(it, wall, now) }
        if (archive != null) controller.behindLive.value = (now - wall).coerceAtLeast(0)
    }

    /** What the player holds, in wall-clock time: the archive, or the timeshift window. */
    private fun loaded(now: Long): CatchupRules.Span? {
        archive?.let { a ->
            val end = a.startWallMs + (player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: a.durationMs)
            return CatchupRules.Span(a.startWallMs, end - CatchupRules.ARCHIVE_TAIL_MS)
        }
        if (!controller.timeshift.value) return null
        val length = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return null
        // Position 0 is the window's oldest segment.
        val start = playheadWallMs(now) - player.currentPosition
        return CatchupRules.Span(start + WINDOW_EDGE_MS, start + length)
    }

    /** A seek to [offsetMs] into the bar's programme. */
    fun seek(offsetMs: Long) {
        val program = timeline.value?.program ?: return
        seekWall(program.startMs + offsetMs)
    }

    fun seekWall(wallMs: Long) {
        val now = System.currentTimeMillis()
        when (val seek = CatchupRules.decide(wallMs, now, loaded(now), programs, archiveWindow)) {
            CatchupRules.Seek.Live -> goLive()
            is CatchupRules.Seek.InPlayer -> player.seekTo(seek.positionMs)
            is CatchupRules.Seek.Archive -> openArchive(seek.program, seek.offsetMs)
            CatchupRules.Seek.Nothing -> AppLog.i("Catchup", "no archive at ${(now - wallMs) / 1000}s behind live")
        }
    }

    /** Back to the live edge: the channel tuned again from the archive, or the window's edge. */
    fun goLive() {
        request?.cancel()
        if (archive == null) {
            if (controller.timeshift.value) player.seekToDefaultPosition()
            player.play()
            return
        }
        AppLog.i("Catchup", "back to live from ${controller.behindLive.value / 1000}s behind")
        archive = null
        inArchive.value = false
        controller.behindLive.value = 0
        controller.liveRetune?.unpark()
    }

    private fun openArchive(program: EpgProgram, offsetMs: Long) {
        request?.cancel()
        request = controller.scope.launch {
            AppLog.i("Catchup", "archive ${program.title} from ${offsetMs / 1000}s")
            controller.switching.value = true
            try {
                val start = ApiClient.liveCatchup(channel, program, (offsetMs / 1000).toInt())
                controller.liveRetune?.park()
                // The provider may round the start down (whole minutes): seek on to the point asked for.
                val startWall = program.startMs + start.offsetSeconds * 1000L
                val first = (program.startMs + offsetMs - startWall).coerceAtLeast(0)
                archive = Archive(program, startWall, start.durationSeconds * 1000L, first)
                inArchive.value = true
                player.setMediaItem(MediaItem.fromUri(ApiClient.catchupStreamUrl(start, channel, program)), first)
                player.playWhenReady = true
                player.prepare()
                AppLog.i("Catchup", "archive from ${start.offsetSeconds}s for ${start.durationSeconds}s")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Catchup", "archive failed: ${e.message}")
                controller.notice.value = "Catch-up isn't available for that point"
            } finally {
                controller.switching.value = false
            }
        }
    }

    /** The archive ended: on into a fresh one from there, or live. True when it was the archive's. */
    fun ended(): Boolean {
        val a = archive ?: return false
        val now = System.currentTimeMillis()
        val wall = a.startWallMs + player.currentPosition
        when (val next = CatchupRules.continueAfter(wall, now, player.currentPosition - a.firstPositionMs, programs, archiveWindow)) {
            is CatchupRules.Seek.Archive -> {
                AppLog.i("Catchup", "archive ended ${(now - wall) / 1000}s behind live; continuing")
                openArchive(next.program, next.offsetMs)
            }
            else -> goLive()
        }
        return true
    }

    /** A playback error while the archive plays: back to live rather than the error screen. */
    fun failed(message: String?): Boolean {
        if (archive == null) return false
        AppLog.w("Catchup", "archive error: $message; back to live")
        controller.notice.value = "Catch-up stopped; back to live"
        goLive()
        return true
    }

    fun release() {
        schedule.cancel()
        request?.cancel()
    }

    private companion object {
        /** How far back the bar's schedule reaches (the archive may hold more). */
        const val SCHEDULE_BACK_MINUTES = 12 * 60L
        const val SCHEDULE_REFRESH_MS = 15 * 60_000L
        /** The window's oldest segment may be pruned under the player: seek no closer to it. */
        const val WINDOW_EDGE_MS = 10_000L
    }
}
