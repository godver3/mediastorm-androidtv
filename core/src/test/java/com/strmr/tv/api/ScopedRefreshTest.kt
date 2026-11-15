package com.strmr.tv.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScopedRefreshTest {
    @Test fun `concurrent callers share one active rotation`() = runTest {
        val gate=RequestContext({0L},{ });val rotations=ScopedRefresh<String>(backgroundScope);val ready=CompletableDeferred<Unit>();var starts=0
        val first=async {rotations.run(gate.captureAuthentication()){starts++;ready.await();"rotated"}}
        val second=async {rotations.run(gate.captureAuthentication()){starts++;"unexpected"}}
        runCurrent();assertEquals(1,starts);ready.complete(Unit);assertEquals("rotated",first.await());assertEquals("rotated",second.await())
    }
    @Test fun `a cancelled waiter does not detach the still running shared rotation`() = runTest {
        val gate=RequestContext({0L},{ });val rotations=ScopedRefresh<String>(backgroundScope);val ready=CompletableDeferred<Unit>();var starts=0
        val first=async {rotations.run(gate.captureAuthentication()){starts++;ready.await();"rotated"}}
        runCurrent();first.cancelAndJoin()
        val second=async {rotations.run(gate.captureAuthentication()){starts++;"unexpected"}}
        runCurrent();assertEquals(1,starts);ready.complete(Unit);assertEquals("rotated",second.await())
    }
    @Test fun `new authentication never joins an old in flight rotation`() = runTest {
        var epoch=0L;val gate=RequestContext({epoch},{epoch++});val rotations=ScopedRefresh<String>(backgroundScope);val oldReply=CompletableDeferred<Unit>()
        val first=async {rotations.run(gate.captureAuthentication()){oldReply.await();"old"}}
        runCurrent();gate.changeAuthentication(change={true})
        val second=async {rotations.run(gate.captureAuthentication()){"new"}}
        assertEquals("new",second.await());oldReply.complete(Unit)
        try{first.await();fail("Old rotation escaped its context")}catch(_:CancellationException){}
    }
    @Test fun `a profile switch can retain an account rotation`() = runTest {
        var epoch=0L;val gate=RequestContext({epoch},{epoch++});val rotations=ScopedRefresh<String>(backgroundScope);val ready=CompletableDeferred<Unit>()
        val first=async {rotations.run(gate.captureAuthentication()){ready.await();"rotated"}}
        runCurrent();gate.changeProfile{};ready.complete(Unit);assertEquals("rotated",first.await())
    }
}
