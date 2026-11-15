package com.strmr.tv.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class ScopedConnectionTest {
    private suspend fun cancelled(block:suspend()->Unit){try{block();fail("Old connection survived")}catch(_:CancellationException){}}
    @Test fun `the same identity shares one opening`() = runTest {
        val connection=ScopedConnection<String>(backgroundScope);val identity=Any();val ready=CompletableDeferred<Unit>();var opens=0
        val first=async{connection.connect(identity,{true}){opens++;ready.await();"same-peer"}}
        val second=async{connection.connect(identity,{true}){opens++;"unexpected"}}
        runCurrent();assertEquals(1,opens);ready.complete(Unit);assertEquals("same-peer",first.await());assertEquals("same-peer",second.await())
    }
    @Test fun `cancelling a waiter preserves the shared native opening`() = runTest {
        val connection=ScopedConnection<String>(backgroundScope);val identity=Any();val ready=CompletableDeferred<Unit>();var opens=0
        val first=async{connection.connect(identity,{true}){opens++;ready.await();"same-peer"}}
        runCurrent();first.cancelAndJoin();val second=async{connection.connect(identity,{true}){opens++;"unexpected"}}
        runCurrent();assertEquals(1,opens);ready.complete(Unit);assertEquals("same-peer",second.await())
    }
    @Test fun `a new peer waits for an old native operation to finish stopping`() = runTest {
        val connection=ScopedConnection<String>(backgroundScope);val old=Any();val fresh=Any();var active=old
        val finish=CompletableDeferred<Unit>();val order=mutableListOf<String>()
        val first=async{connection.connect(old,{active===old}){order+="old-start";withContext(NonCancellable){finish.await()};order+="old-finished";"old"}}
        runCurrent();active=fresh
        val second=async{connection.connect(fresh,{active===fresh}){order+="new-start";"new"}}
        runCurrent();assertEquals(listOf("old-start"),order);finish.complete(Unit)
        cancelled{first.await()};assertEquals("new",second.await());assertEquals(listOf("old-start","old-finished","new-start"),order)
    }
    @Test fun `pairing replacement serializes the native resource and rejects queued old peers`() = runTest {
        val connection=ScopedConnection<String>(backgroundScope);val old=Any();val fresh=Any();var active=old
        val pairing=CompletableDeferred<Unit>();val replacement=async{connection.replace {pairing.await();active=fresh}}
        runCurrent();var opened=false
        val stale=async{connection.connect(old,{active===old}){opened=true;"old"}}
        runCurrent();assertFalse(opened);pairing.complete(Unit);replacement.await();cancelled{stale.await()};assertFalse(opened)
        assertEquals("new",connection.connect(fresh,{active===fresh}){"new"})
    }
    @Test fun `an identity change discards an otherwise successful open`() = runTest {
        val connection=ScopedConnection<String>(backgroundScope);val identity=Any();var active=true
        cancelled{connection.connect(identity,{active}){active=false;"stale-peer"}}
    }
    @Test fun `unpairing cancels an active opening before it can return`() = runTest {
        val connection=ScopedConnection<String>(backgroundScope);val identity=Any();val finish=CompletableDeferred<Unit>()
        val request=async{connection.connect(identity,{true}){finish.await();"old"}}
        runCurrent();connection.cancelActive();cancelled{request.await()}
        assertEquals("fresh",connection.connect(Any(),{true}){"fresh"})
    }

}
