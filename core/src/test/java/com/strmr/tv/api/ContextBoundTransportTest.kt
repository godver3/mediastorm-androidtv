package com.strmr.tv.api

import java.io.IOException
import java.net.ConnectException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ContextBoundTransportTest {
    private class Fixture {
        var epoch=0L
        val gate=RequestContext({epoch}) {epoch++}
        val context=gate.capture()
        var recoveries=0
        var failures=0
        var sends=0
        var recover:suspend()->Boolean={true}
        fun transport(remote:Boolean=true)=ContextBoundTransport(context,remote,{recoveries++;recover()},{failures++})
    }
    private suspend fun cancelled(block:suspend()->Unit){try{block();fail("Stale transport survived")}catch(_:CancellationException){}}

    @Test fun `an unchanged remote connection retries once`() = runTest {
        val f=Fixture();val reply=f.transport().execute {f.sends++;if(f.sends==1)throw ConnectException("Synthetic disconnected peer");"ok"}
        assertEquals("ok",reply);assertEquals(2,f.sends);assertEquals(1,f.recoveries);assertEquals(0,f.failures)
    }
    @Test fun `a second transport failure cannot repeat recovery`() = runTest {
        val f=Fixture();try{f.transport().execute {f.sends++;throw ConnectException("Synthetic offline peer")};fail()}catch(_:ConnectException){}
        assertEquals(2,f.sends);assertEquals(1,f.recoveries);assertEquals(1,f.failures)
    }
    @Test fun `direct connections and ordinary IO failures do not reconnect`() = runTest {
        for(remote in listOf(false,true)){
            val f=Fixture();try{f.transport(remote).execute {f.sends++;if(remote)throw IOException("Synthetic data error") else throw ConnectException("Synthetic direct peer")};fail()}catch(_:IOException){}
            assertEquals(1,f.sends);assertEquals(0,f.recoveries);assertEquals(1,f.failures)
        }
    }
    @Test fun `an identity change before a transport error suppresses old callbacks`() = runTest {
        val f=Fixture();cancelled{f.transport().execute {f.sends++;f.gate.changeAuthentication(change={true});throw ConnectException("Synthetic offline peer")}}
        assertEquals(1,f.sends);assertEquals(0,f.recoveries);assertEquals(0,f.failures)
    }
    @Test fun `a server or profile change during recovery cancels the prepared retry`() = runTest {
        for(profileOnly in listOf(false,true)){
            val f=Fixture();val entered=CompletableDeferred<Unit>();val resume=CompletableDeferred<Unit>()
            f.recover={entered.complete(Unit);resume.await();true}
            val request=async{f.transport().execute {f.sends++;throw ConnectException("Synthetic disconnected peer")}}
            entered.await();if(profileOnly)f.gate.changeProfile{} else f.gate.changeAuthentication(change={true});resume.complete(Unit)
            cancelled{request.await()};assertEquals(1,f.sends);assertEquals(0,f.failures)
        }
    }
    @Test fun `a failed reconnect does not attempt another request`() = runTest {
        val f=Fixture();f.recover={false}
        try{f.transport().execute {f.sends++;throw ConnectException("Synthetic offline peer")};fail()}catch(_:ConnectException){}
        assertEquals(1,f.sends);assertEquals(1,f.recoveries)
    }
    @Test fun `late successful transport answers are discarded after profile change`() = runTest {
        val f=Fixture();cancelled{f.transport().execute {f.sends++;f.gate.changeProfile{};"old-profile-response"}}
        assertEquals(1,f.sends);assertEquals(0,f.recoveries)
    }
}
