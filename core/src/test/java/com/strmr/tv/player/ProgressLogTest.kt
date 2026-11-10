package com.strmr.tv.player

import com.strmr.tv.api.ProgressReply
import com.strmr.tv.api.ResumeState
import com.strmr.tv.player.ProgressLog.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressLogTest {
    private val lines = mutableListOf<Pair<Boolean, String>>()
    private val log = ProgressLog("Masterplan") { warn, message -> lines += warn to message }
    private val notOffered = ProgressReply(2.0, ResumeState(120.0, 6000.0, 2.0, eligible = false))
    private val offered = ProgressReply(20.0, ResumeState(1200.0, 6000.0, 20.0, eligible = true))

    @Test fun periodicReportsLogOnlyTheFirstSave() {
        log.saved(Kind.PERIODIC, 10.0, 6000.0, notOffered)
        log.saved(Kind.PERIODIC, 20.0, 6000.0, notOffered)
        assertEquals(listOf(false to "Masterplan: first periodic report at 0:10/1:40:00 (0.2%) saved: stored at 2.0%, resume not offered"), lines)
    }

    @Test fun closeReportCarriesTheResumeDecision() {
        log.saved(Kind.CLOSE, 1200.0, 6000.0, offered)
        assertEquals("Masterplan: close report at 20:00/1:40:00 (20.0%) saved: resume offered at 20:00", lines.single().second)
    }

    @Test fun failedPeriodicReportsAreThrottledAndRecoveryIsLogged() {
        repeat(7) { log.failed(Kind.PERIODIC, 60.0, 6000.0, RuntimeException("connect failed")) }
        assertEquals(listOf(1, 6), lines.map { it.second.substringAfter("failed (").substringBefore(" in").toInt() })
        assertEquals(true, lines.all { it.first })
        log.saved(Kind.PERIODIC, 70.0, 6000.0, notOffered)
        assertEquals(false to "Masterplan: periodic report at 1:10/1:40:00 (1.2%) saved: stored at 2.0%, resume not offered (after 7 failed)", lines.last())
    }

    @Test fun everyFailedCloseReportIsLogged() {
        log.failed(Kind.PERIODIC, 60.0, 6000.0, RuntimeException("timeout"))
        log.failed(Kind.CLOSE, 61.0, 6000.0, RuntimeException("timeout"))
        assertEquals(2, lines.size)
        assertEquals("Masterplan: close report at 1:01/1:40:00 (1.0%) failed (2 in a row): timeout", lines.last().second)
    }
}
