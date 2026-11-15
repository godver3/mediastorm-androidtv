package com.strmr.tv.api

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class RequestContextTest {
    private class Fixture {
        val epoch = AtomicLong()
        val gate = RequestContext(epoch::get) { epoch.incrementAndGet() }
        var identity = "synthetic-account-a"
        fun change(value: String) = gate.changeAuthentication(change = { val changed = identity != value; identity = value; changed })
    }
    private fun cancelled(block: () -> Unit) { try { block(); fail("Stale context accepted") } catch (_: CancellationException) {} }

    @Test fun `a profile switch invalidates a request but preserves its account rotation`() {
        val f = Fixture(); val request = f.gate.capture(); val rotation = f.gate.captureAuthentication()
        f.gate.changeProfile {}
        cancelled { request.check() }; rotation.check()
    }
    @Test fun `a server account or credential replacement invalidates both scopes`() {
        for (identity in listOf("synthetic-server-b","synthetic-account-b","synthetic-pairing-b")) {
            val f = Fixture(); val request = f.gate.capture(); val rotation = f.gate.captureAuthentication()
            f.change(identity); cancelled { request.check() }; cancelled { rotation.check() }
        }
    }
    @Test fun `returning to the same identity never revives old requests`() {
        val f=Fixture(); val old=f.gate.capture(); f.change("synthetic-account-b"); f.change("synthetic-account-a")
        cancelled { old.check() }; f.gate.capture().check()
    }
    @Test fun `a captured caller epoch rejects a request before preparing headers`() {
        val f=Fixture(); val captured=f.epoch.get(); f.gate.changeProfile {}; var prepared=false
        cancelled { f.gate.capture(captured).use { prepared=true } }; assertFalse(prepared)
    }
    @Test fun `an unchanged assignment preserves peers and does not persist again`() {
        val f=Fixture(); val context=f.gate.capture(); var saves=0
        f.gate.changeAuthentication(change={false},afterChange={saves++});context.check();assertEquals(0,saves)
    }
    @Test fun `failed persistence cannot leave changed identity in the old context`() {
        val f=Fixture();val old=f.gate.capture()
        try { f.gate.changeAuthentication(change={f.identity="synthetic-account-b";true},afterChange={error("Synthetic disk failure")}) }catch(_:IllegalStateException){}
        cancelled { old.check() }
    }
    @Test fun `credential snapshots and identity changes are serialized`() {
        val f=Fixture();val scope=f.gate.capture();val entered=CountDownLatch(1);val finish=CountDownLatch(1);val changing=CountDownLatch(1)
        val workers=Executors.newFixedThreadPool(2)
        try {
            val snapshot=workers.submit<String> {scope.use { entered.countDown();check(finish.await(2,TimeUnit.SECONDS));f.identity }}
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            val change=workers.submit {changing.countDown();f.change("synthetic-account-b")}
            assertTrue(changing.await(2,TimeUnit.SECONDS));finish.countDown()
            assertEquals("synthetic-account-a",snapshot.get(2,TimeUnit.SECONDS));change.get(2,TimeUnit.SECONDS)
            cancelled {scope.check()}
        } finally {finish.countDown();workers.shutdownNow()}
    }
}
