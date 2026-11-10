package com.strmr.mobile.ui.live

import com.strmr.tv.live.EpgProgram
import com.strmr.tv.live.LiveCatchup
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.Recording
import com.strmr.tv.live.SeriesRule
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveActionsTest {
    private val now = Instant.parse("2026-11-02T22:00:00Z").toEpochMilli()
    private val channel = LiveChannel(id = "c", name = "Chan", url = "http://h/c.ts")
    private val archived = channel.copy(catchup = LiveCatchup(days = 2, mode = "xc"))
    private fun program(start: String, stop: String) = EpgProgram(title = "Show", start = "2026-11-02T$start:00Z", stop = "2026-11-02T$stop:00Z")
    private val onNow = program("21:30", "22:30")
    private val later = program("23:00", "23:30")
    private val past = program("20:00", "21:00")

    @Test fun anEndedProgrammeReplaysThenTunesLive() {
        assertEquals(listOf("Watch from Start", "Watch Chan Live"), LiveActions.watch(archived, past, onNow, now).map { it.label })
        assertEquals(listOf("Watch Chan Live"), LiveActions.watch(channel, past, onNow, now).map { it.label })
    }

    @Test fun onNowOffersStartOverWhenArchived() {
        assertEquals(listOf("Watch Live", "Start Over"), LiveActions.watch(archived, null, onNow, now).map { it.label })
        assertEquals(listOf("Watch Live"), LiveActions.watch(channel, onNow, onNow, now).map { it.label })
    }

    @Test fun recordWordingFollowsTheTvMenu() {
        assertEquals(
            listOf("Record Current Program", "Record Every Airing", "Record New Episodes Only", "Record 2 Hour Block"),
            LiveActions.record(null, onNow, null, null, now).map { it.label },
        )
        assertEquals("Record", LiveActions.record(onNow, onNow, null, null, now).first().label)
        assertEquals(listOf("Record Once", "Record Series"), LiveActions.record(later, later, null, null, now).take(2).map { it.label })
    }

    @Test fun anExistingRecordingAndRuleSwitchOrCancel() {
        val pending = Recording(id = "r", status = "pending")
        val rule = SeriesRule(id = "s", pattern = "Show", newOnly = true)
        val labels = LiveActions.record(later, later, pending, rule, now).map { it.label }
        assertEquals(listOf("Cancel Scheduled Recording", "Record Every Airing Instead", "Cancel Series Recording", "Record 2 Hour Block"), labels)
        val regex = SeriesRule(id = "x", matchType = "regex", pattern = "News.*")
        assertTrue(LiveActions.record(later, later, Recording(id = "r", status = "running"), regex, now).map { it.label }
            .containsAll(listOf("Stop Recording", "Record New Episodes Only Instead", "Delete Rule \"News.*\"")))
    }

    @Test fun nothingOnNowStillRecordsABlock() {
        assertEquals(listOf("Record 2 Hour Block"), LiveActions.record(null, null, null, null, now).map { it.label })
    }

    @Test fun channelChoices() {
        assertEquals(listOf("Add to Favorites", "Hide Channel"), LiveActions.channel(channel, false, false).map { it.label })
        assertEquals(listOf("Remove from Favorites", "Show Channel"), LiveActions.channel(channel, true, true).map { it.label })
    }
}
