package com.strmr.tv.player

import com.strmr.tv.live.EpgProgram
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CatchupRulesTest {
    private val hour = 3_600_000L
    private val now = Instant.parse("2026-11-04T20:30:00Z").toEpochMilli()
    private fun program(start: String, stop: String, title: String = start) =
        EpgProgram(title, start = "2026-11-04T${start}:00Z", stop = "2026-11-04T${stop}:00Z")
    private val programs = listOf(program("18:00", "19:00", "Earlier"), program("19:00", "20:00", "Before"), program("20:00", "22:00", "Game"))
    private val game = programs[2]
    private val week = 7 * 24 * hour

    @Test
    fun programAtPicksTheOneAiring() {
        assertEquals("Game", CatchupRules.programAt(programs, now)?.title)
        assertEquals("Before", CatchupRules.programAt(programs, now - 45 * 60_000)?.title)
        assertEquals(null, CatchupRules.programAt(programs, now + 2 * hour))
    }

    @Test
    fun nearLiveInTheWindowStaysLive() {
        val window = CatchupRules.Span(startWallMs = now - 60_000, endWallMs = now)
        assertEquals(CatchupRules.Seek.Live, CatchupRules.decide(now - 5_000, now, window, programs, week))
    }

    @Test
    fun rewindFromAFreshTuneReachesAMinuteBack() {
        // One 10 s press back with nothing held: a minute back, into the archive.
        assertEquals(CatchupRules.Seek.Archive(game, 29 * 60_000), CatchupRules.decide(now - 10_000, now, null, programs, week))
        assertEquals(CatchupRules.Seek.Archive(game, 29 * 60_000), CatchupRules.decide(now - 40_000, now, null, programs, week))
        // Just before a short timeshift window: also a minute back.
        val window = CatchupRules.Span(startWallMs = now - 20_000, endWallMs = now)
        assertEquals(CatchupRules.Seek.Archive(game, 29 * 60_000), CatchupRules.decide(now - 30_000, now, window, programs, week))
    }

    @Test
    fun forwardPastTheArchiveNearNowGoesLive() {
        val archive = CatchupRules.Span(startWallMs = now - 20 * 60_000, endWallMs = now - 10 * 60_000)
        assertEquals(CatchupRules.Seek.Live, CatchupRules.decide(now - 30_000, now, archive, programs, week))
    }

    @Test
    fun insideTheWindowSeeksThePlayer() {
        val window = CatchupRules.Span(startWallMs = now - 10 * 60_000, endWallMs = now, startPositionMs = 0)
        assertEquals(CatchupRules.Seek.InPlayer(5 * 60_000), CatchupRules.decide(now - 5 * 60_000, now, window, programs, week))
    }

    @Test
    fun beforeTheWindowOpensTheArchive() {
        val window = CatchupRules.Span(startWallMs = now - 10 * 60_000, endWallMs = now)
        // 25 minutes behind live: 5 minutes into the game.
        assertEquals(CatchupRules.Seek.Archive(game, 5 * 60_000), CatchupRules.decide(now - 25 * 60_000, now, window, programs, week))
        // Without a window (a direct tune) any point back is the archive.
        assertEquals(CatchupRules.Seek.Archive(game, 29 * 60_000), CatchupRules.decide(now - 60_000, now, null, programs, week))
    }

    @Test
    fun outsideTheArchiveOrGuideDoesNothing() {
        assertEquals(CatchupRules.Seek.Nothing, CatchupRules.decide(now - 2 * hour, now, null, programs, hour))
        assertEquals(CatchupRules.Seek.Nothing, CatchupRules.decide(now - 5 * hour, now, null, programs, week))
    }

    @Test
    fun archiveEndCarriesOnIntoTheNextProgramme() {
        // An archive of "Before" ran to its stop + 2 min tail: on into the game, 2 minutes in.
        val wall = game.startMs + 2 * 60_000
        assertEquals(CatchupRules.Seek.Archive(game, 2 * 60_000), CatchupRules.continueAfter(wall, now, 20 * 60_000, programs, week))
    }

    @Test
    fun archiveEndAtLiveOrEmptyGoesLive() {
        assertEquals(CatchupRules.Seek.Live, CatchupRules.continueAfter(now - 20_000, now, 10 * 60_000, programs, week))
        assertEquals(CatchupRules.Seek.Live, CatchupRules.continueAfter(now - 10 * 60_000, now, 1_000, programs, week))
    }

    @Test
    fun timelineSpansTheProgramme() {
        val t = CatchupRules.timeline(game, playheadWallMs = now - 10 * 60_000, nowMs = now)
        assertEquals(20 * 60_000L, t.positionMs)
        assertEquals(2 * hour, t.lengthMs)
        assertEquals(30 * 60_000L, t.availableMs)
        assertEquals(10 * 60_000L, t.behindLiveMs)
    }
}
