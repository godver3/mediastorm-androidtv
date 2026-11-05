package com.strmr.tv.player

import com.strmr.tv.live.EpgProgram

/**
 * The bar of a live channel with provider catch-up: the programme at the playhead, start to
 * stop, in ms from its start. [availableMs] is the live edge (now), past which nothing has aired;
 * [positionMs] the playhead, which may be in the timeshift window or the provider's archive.
 */
data class ProgrammeTimeline(
    val program: EpgProgram,
    val positionMs: Long,
    val lengthMs: Long,
    val availableMs: Long,
) {
    val behindLiveMs: Long get() = (availableMs - positionMs).coerceAtLeast(0)
}

/**
 * Where a seek on a catch-up channel lands, in wall-clock time: inside what the player has
 * loaded (the timeshift window, or the archive file playing), back at live, or a new archive
 * request for the programme at that point. Pure, so it is unit-tested.
 */
object CatchupRules {
    /** Within this of now counts as live. */
    const val AT_LIVE_MS = Timeshift.AT_LIVE_TOLERANCE_MS
    /** An archive that would end this close to now isn't worth opening: live is as good. */
    const val MIN_ARCHIVE_MS = 60_000L
    /** Short of an archive's end, so a seek there doesn't land on its last frames and end at once. */
    const val ARCHIVE_TAIL_MS = 5_000L

    /** What the player can seek to without loading anything: wall-clock [startWallMs, endWallMs] from position [startPositionMs]. */
    data class Span(val startWallMs: Long, val endWallMs: Long, val startPositionMs: Long = 0) {
        operator fun contains(wallMs: Long) = wallMs in startWallMs..endWallMs
        fun position(wallMs: Long) = startPositionMs + (wallMs - startWallMs)
    }

    sealed interface Seek {
        data object Live : Seek
        data class InPlayer(val positionMs: Long) : Seek
        data class Archive(val program: EpgProgram, val offsetMs: Long) : Seek
        data object Nothing : Seek
    }

    /** The programme airing at [wallMs]: the last one to start at or before it that hasn't stopped. */
    fun programAt(programs: List<EpgProgram>, wallMs: Long): EpgProgram? =
        programs.lastOrNull { it.startMs in 1..wallMs && wallMs < it.stopMs }

    /**
     * A rewind before what the player holds (or from a tune holding nothing) goes at least
     * [MIN_ARCHIVE_MS] back, so one press back from live reaches the archive instead of landing
     * "at live" and doing nothing.
     */
    fun decide(targetWallMs: Long, nowMs: Long, loaded: Span?, programs: List<EpgProgram>, archiveWindowMs: Long): Seek {
        if (loaded != null && targetWallMs in loaded) {
            return if (nowMs - targetWallMs <= AT_LIVE_MS) Seek.Live else Seek.InPlayer(loaded.position(targetWallMs))
        }
        val rewinding = targetWallMs < nowMs && (loaded == null || targetWallMs < loaded.startWallMs)
        if (rewinding) return archiveAt(minOf(targetWallMs, nowMs - MIN_ARCHIVE_MS), nowMs, programs, archiveWindowMs)
        if (nowMs - targetWallMs <= AT_LIVE_MS) return Seek.Live
        return archiveAt(targetWallMs, nowMs, programs, archiveWindowMs)
    }

    /** The archive at [targetWallMs], or live when that is too close to now to be worth opening. */
    private fun archiveAt(targetWallMs: Long, nowMs: Long, programs: List<EpgProgram>, archiveWindowMs: Long): Seek {
        if (nowMs - targetWallMs < MIN_ARCHIVE_MS) return Seek.Live
        if (targetWallMs < nowMs - archiveWindowMs) return Seek.Nothing
        val program = programAt(programs, targetWallMs) ?: return Seek.Nothing
        return Seek.Archive(program, targetWallMs - program.startMs)
    }

    /**
     * An archive played to its end at [playheadWallMs] (it ran up to what had aired when it was
     * requested): carry on from there in a fresh one, or go live once that is now. [playedMs] is
     * how much of it played; one that played almost nothing would only be asked for again.
     */
    fun continueAfter(playheadWallMs: Long, nowMs: Long, playedMs: Long, programs: List<EpgProgram>, archiveWindowMs: Long): Seek {
        if (playedMs < MIN_PLAYED_MS) return Seek.Live
        return archiveAt(playheadWallMs, nowMs, programs, archiveWindowMs) as? Seek.Archive ?: Seek.Live
    }

    /** Less than this of an archive played before it ended: the provider has nothing more yet. */
    const val MIN_PLAYED_MS = 5_000L

    fun timeline(program: EpgProgram, playheadWallMs: Long, nowMs: Long) = ProgrammeTimeline(
        program,
        positionMs = (playheadWallMs - program.startMs).coerceAtLeast(0),
        lengthMs = (program.stopMs - program.startMs).coerceAtLeast(1),
        availableMs = (nowMs - program.startMs).coerceIn(0, (program.stopMs - program.startMs).coerceAtLeast(1)),
    )
}
