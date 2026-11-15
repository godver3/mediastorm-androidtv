package com.strmr.tv.live

import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LiveGuideContextTest {
    private val channel=LiveChannel("synthetic-channel","Synthetic channel","http://127.0.0.1/synthetic",tvgId="synthetic-epg")
    private fun program()=EpgProgram("Synthetic programme",start=Instant.now().minusSeconds(60).toString(),stop=Instant.now().plusSeconds(1800).toString())

    @Test fun `an old queued guide batch cannot query the replacement profile`() = runTest {
        var epoch=0L;var calls=0
        val guide=LiveGuideData(backgroundScope,{true},context=epoch,currentEpoch={epoch},loadNowNext={calls++;emptyList()},loadSchedule={_,_,_->calls++;emptyMap()})
        guide.needNowNext(channel);epoch++;advanceTimeBy(121);runCurrent()
        assertEquals(0,calls);assertTrue(guide.nowNext.isEmpty())
    }
    @Test fun `a late now next response never enters a new profile view`() = runTest {
        var epoch=0L;val response=CompletableDeferred<Unit>();var calls=0
        val guide=LiveGuideData(backgroundScope,{true},context=epoch,currentEpoch={epoch},loadNowNext={calls++;response.await();listOf(EpgNowPlaying(channel.guideKey,program()))},loadSchedule={_,_,_->emptyMap()})
        guide.needNowNext(channel);advanceTimeBy(121);runCurrent();assertEquals(1,calls)
        epoch++;response.complete(Unit);runCurrent();assertTrue(guide.nowNext.isEmpty())
        guide.needNowNext(channel);advanceTimeBy(121);runCurrent();assertEquals(1,calls)
    }
    @Test fun `a late schedule response is discarded after identity change`() = runTest {
        var epoch=0L;val response=CompletableDeferred<Unit>();var calls=0
        val guide=LiveGuideData(backgroundScope,{true},context=epoch,currentEpoch={epoch},loadNowNext={emptyList()},loadSchedule={_,_,_->calls++;response.await();mapOf(channel.guideKey to listOf(program()))})
        guide.needSchedule(channel,guide.guideBase,guide.guideBase+LiveGuideData.HALF_HOUR);advanceTimeBy(151);runCurrent();assertEquals(1,calls)
        epoch++;response.complete(Unit);runCurrent();assertTrue(guide.schedules.isEmpty())
    }
    @Test fun `current guide responses retain channel specific schedules and now next`() = runTest {
        val show=program();var nowCalls=0;var scheduleCalls=0
        val guide=LiveGuideData(backgroundScope,{true},context=0,currentEpoch={0L},loadNowNext={nowCalls++;listOf(EpgNowPlaying(channel.guideKey,show))},loadSchedule={_,_,_->scheduleCalls++;mapOf(channel.guideKey to listOf(show))})
        guide.needNowNext(channel);guide.needSchedule(channel,guide.guideBase,guide.guideBase+LiveGuideData.HALF_HOUR);advanceTimeBy(151);runCurrent()
        assertEquals(1,nowCalls);assertEquals(1,scheduleCalls);assertEquals(show,guide.current(channel));assertEquals(listOf(show),guide.schedules[channel.guideKey])
    }
    @Test fun `an old guide cannot begin a manual refetch`() = runTest {
        var epoch=0L;var calls=0
        val guide=LiveGuideData(backgroundScope,{true},context=epoch,currentEpoch={epoch},loadNowNext={calls++;emptyList()},loadSchedule={_,_,_->calls++;emptyMap()})
        epoch++;try{guide.refetch();fail("Old guide refetched")}catch(_:CancellationException){}
        assertEquals(0,calls)
    }
}
