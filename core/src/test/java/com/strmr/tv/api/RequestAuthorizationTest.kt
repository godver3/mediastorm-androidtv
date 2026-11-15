package com.strmr.tv.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RequestAuthorizationTest {
    private fun rejected() = ApiError.Http(HttpFailure(401,"{}"))
    private class Fixture {
        var epoch=0L
        val gate=RequestContext({epoch}) {epoch++}
        val context=gate.capture()
        var token="synthetic-token-a"
        var nearExpiry=false
        var refreshes=0
        var signOuts=0
        val sent=mutableListOf<String>()
        var rotate: suspend () -> ApiClient.RefreshOutcome = {token="synthetic-token-rotated";ApiClient.RefreshOutcome.REFRESHED}
        var send: suspend (String) -> String = {"ok"}
        fun request()=RequestAuthorization(context,{token},{nearExpiry},{refreshes++;rotate()},{signOuts++},{sent+=it;send(it)})
        fun change(){gate.changeAuthentication(change={token="synthetic-token-b";true})}
    }
    private suspend fun cancelled(block:suspend()->Unit) {try{block();fail("Stale request survived")}catch(_:CancellationException){}}

    @Test fun `a valid request sends the current token once`() = runTest {
        val f=Fixture();assertEquals("ok",f.request().execute());assertEquals(listOf("synthetic-token-a"),f.sent);assertEquals(0,f.refreshes)
    }
    @Test fun `expiry rotates once before the first send`() = runTest {
        val f=Fixture();f.nearExpiry=true;assertEquals("ok",f.request().execute());assertEquals(listOf("synthetic-token-rotated"),f.sent);assertEquals(1,f.refreshes)
    }
    @Test fun `an unauthorized response rotates and retries exactly once`() = runTest {
        val f=Fixture();f.send={if(f.sent.size==1)throw rejected();"ok"};assertEquals("ok",f.request().execute());assertEquals(listOf("synthetic-token-a","synthetic-token-rotated"),f.sent)
    }
    @Test fun `a peer rotation reuses the new token without another rotation`() = runTest {
        val f=Fixture();f.send={if(f.sent.size==1){f.context.use{f.token="synthetic-peer-rotation"};throw rejected()};"ok"}
        assertEquals("ok",f.request().execute());assertEquals(0,f.refreshes);assertEquals("synthetic-peer-rotation",f.sent.last())
    }
    @Test fun `a second unauthorized response cannot loop`() = runTest {
        val f=Fixture();f.send={throw rejected()};try{f.request().execute();fail()}catch(_:ApiError){}
        assertEquals(2,f.sent.size);assertEquals(1,f.refreshes)
    }
    @Test fun `wrong PIN never rotates or signs out the account`() = runTest {
        val f=Fixture();f.send={throw ApiError.Http(HttpFailure(401,"{\"code\":\"pin_invalid\"}"))};try{f.request().execute();fail()}catch(_:ApiError){}
        assertEquals(0,f.refreshes);assertEquals(0,f.signOuts);assertEquals(1,f.sent.size)
    }
    @Test fun `a rejected current rotation signs out only the current request`() = runTest {
        val f=Fixture();f.nearExpiry=true;f.rotate={ApiClient.RefreshOutcome.REJECTED};try{f.request().execute();fail()}catch(_:ApiError.SignedOut){}
        assertEquals(1,f.signOuts);assertTrue(f.sent.isEmpty())
    }
    @Test fun `an unavailable rotation retains the original error without sign out`() = runTest {
        val f=Fixture();f.send={throw rejected()};f.rotate={ApiClient.RefreshOutcome.FAILED};try{f.request().execute();fail()}catch(_:ApiError){}
        assertEquals(0,f.signOuts);assertEquals(1,f.sent.size)
    }
    @Test fun `account replacement during rotation cannot send its token to the old request`() = runTest {
        val f=Fixture();f.nearExpiry=true;val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        f.rotate={entered.complete(Unit);release.await();ApiClient.RefreshOutcome.REFRESHED}
        val request=async {f.request().execute()};entered.await();f.change();release.complete(Unit)
        cancelled{request.await()};assertTrue(f.sent.isEmpty());assertEquals("synthetic-token-b",f.token);assertEquals(0,f.signOuts)
    }
    @Test fun `stale rejected rotation never signs out the replacement account`() = runTest {
        val f=Fixture();f.nearExpiry=true;f.rotate={f.change();ApiClient.RefreshOutcome.REJECTED}
        cancelled{f.request().execute()};assertEquals(0,f.signOuts);assertEquals("synthetic-token-b",f.token)
    }
    @Test fun `profile switch after an HTTP answer discards the old response`() = runTest {
        val f=Fixture();f.send={f.gate.changeProfile{};"old-profile-response"}
        cancelled{f.request().execute()};assertEquals(1,f.sent.size);assertEquals(0,f.refreshes)
    }
}
