package com.strmr.tv.app

import com.strmr.tv.api.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScopedProfileUnlockTest {
    private class Fixture {
        var epoch=0L
        var checks=0
        var saved=0
        var cancelled=0
        var verify:suspend()->Boolean={true}
        val profile=Profile("synthetic-profile","Synthetic profile")
        val prompt=ScopedProfileUnlock({epoch}){_,_,expected,accept->checks++;if(expected!=epoch)throw CancellationException("Synthetic stale PIN");val valid=verify();if(!accept())throw CancellationException("Synthetic stale prompt");if(valid)saved++;valid}
        suspend fun ask()=prompt.unlock(profile.id,profile,epoch){cancelled++}
    }
    private suspend fun cancelled(block:suspend()->Unit){try{block();fail("Stale PIN operation survived")}catch(_:CancellationException){}}

    @Test fun `current PIN success closes the prompt and resumes its waiting request`() = runTest {
        val f=Fixture();val waiter=async{f.ask()};runCurrent();val ui=f.prompt.pinChallenge.value
        assertEquals(PinVerification.Unlocked,f.prompt.verify("0000",ui));assertTrue(waiter.await());assertNull(f.prompt.pinChallenge.value);assertEquals(1,f.checks)
    }
    @Test fun `wrong PIN preserves the same prompt for retry`() = runTest {
        val f=Fixture();f.verify={false};val waiter=async{f.ask()};runCurrent();val ui=f.prompt.pinChallenge.value
        assertEquals(PinVerification.Wrong,f.prompt.verify("0000",ui));assertSame(ui,f.prompt.pinChallenge.value)
        f.verify={true};assertEquals(PinVerification.Unlocked,f.prompt.verify("0000",ui));assertTrue(waiter.await())
    }
    @Test fun `network failure preserves the prompt without approving it`() = runTest {
        val f=Fixture();f.verify={throw java.io.IOException("Synthetic offline peer")};val waiter=async{f.ask()};runCurrent();val ui=f.prompt.pinChallenge.value
        assertTrue(f.prompt.verify("0000",ui)is PinVerification.Failed);assertSame(ui,f.prompt.pinChallenge.value);f.prompt.cancel(ui);assertFalse(waiter.await())
    }
    @Test fun `concurrent requests share one prompt and handle cancellation once`() = runTest {
        val f=Fixture();val first=async{f.ask()};val second=async{f.ask()};runCurrent();f.prompt.cancel(f.prompt.pinChallenge.value)
        assertFalse(first.await());assertFalse(second.await());assertEquals(1,f.cancelled)
    }
    @Test fun `cancelling one waiter preserves a prompt still needed by another`() = runTest {
        val f=Fixture();val first=async{f.ask()};val second=async{f.ask()};runCurrent();val ui=f.prompt.pinChallenge.value;first.cancelAndJoin()
        assertSame(ui,f.prompt.pinChallenge.value);f.prompt.verify("0000",ui);assertTrue(second.await())
    }
    @Test fun `an identity change closes the old prompt without changing the new screen`() = runTest {
        val f=Fixture();val waiter=async{f.ask()};runCurrent();f.epoch++;f.prompt.expire()
        cancelled{waiter.await()};assertNull(f.prompt.pinChallenge.value);assertEquals(0,f.cancelled);assertEquals(0,f.checks)
    }
    @Test fun `a stale UI callback cannot verify or cancel a replacement prompt`() = runTest {
        val f=Fixture();val first=async{f.ask()};runCurrent();val old=f.prompt.pinChallenge.value
        f.epoch++;f.prompt.expire();cancelled{first.await()};val second=async{f.ask()};runCurrent();val current=f.prompt.pinChallenge.value
        cancelled{f.prompt.verify("0000",old)};f.prompt.cancel(old);assertSame(current,f.prompt.pinChallenge.value);assertEquals(0,f.checks)
        f.prompt.verify("0000",current);assertTrue(second.await())
    }
    @Test fun `a late PIN answer cannot close a newer prompt`() = runTest {
        val f=Fixture();val reply=CompletableDeferred<Unit>();f.verify={reply.await();true}
        val first=async{f.ask()};runCurrent();val check=async{f.prompt.verify("0000",f.prompt.pinChallenge.value)};runCurrent()
        f.epoch++;f.prompt.expire();cancelled{first.await()};val second=async{f.ask()};runCurrent();val current=f.prompt.pinChallenge.value
        reply.complete(Unit);cancelled{check.await()};assertSame(current,f.prompt.pinChallenge.value)
        f.verify={true};f.prompt.verify("0000",current);assertTrue(second.await())
    }
    @Test fun `the final cancelled waiter does not leave an orphan prompt`() = runTest {
        val f=Fixture();val waiter=async{f.ask()};runCurrent();waiter.cancelAndJoin();assertNull(f.prompt.pinChallenge.value);assertEquals(0,f.cancelled)
    }
    @Test fun `cancelling during a PIN check cannot persist its later unlock`() = runTest {
        val f=Fixture();val reply=CompletableDeferred<Unit>();f.verify={reply.await();true}
        val waiter=async{f.ask()};runCurrent();val ui=f.prompt.pinChallenge.value
        val check=async{f.prompt.verify("0000",ui)};runCurrent();f.prompt.cancel(ui);assertFalse(waiter.await())
        reply.complete(Unit);cancelled{check.await()};assertEquals(0,f.saved);assertNull(f.prompt.pinChallenge.value)
    }

}
