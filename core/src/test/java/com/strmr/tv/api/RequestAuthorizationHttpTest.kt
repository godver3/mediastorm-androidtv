package com.strmr.tv.api

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real loopback HTTP peers; all credentials and routes are synthetic. */
class RequestAuthorizationHttpTest {
    private data class Seen(val path: String, val authorization: String?)
    private class Peer(private val respond: (Seen) -> Pair<Int,String>) : AutoCloseable {
        private val socket=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        private val worker=Executors.newSingleThreadExecutor { r -> Thread(r,"synthetic-http-peer").apply {isDaemon=true} }
        val requests=CopyOnWriteArrayList<Seen>()
        val base="http://127.0.0.1:${socket.localPort}"
        init {worker.submit {
            while(!socket.isClosed) {
                val connection=try{socket.accept()}catch(_:IOException){break}
                connection.use { client ->
                    client.soTimeout=3000
                    val input=client.getInputStream().bufferedReader()
                    val first=input.readLine()?:return@use
                    val headers=mutableMapOf<String,String>()
                    while(true){val line=input.readLine()?:break;if(line.isEmpty())break;val split=line.indexOf(':');if(split>0)headers[line.take(split).lowercase()]=line.drop(split+1).trim()}
                    val seen=Seen(first.split(' ')[1],headers["authorization"]);requests+=seen
                    val(status,body)=respond(seen);val bytes=body.toByteArray()
                    val prefix="HTTP/1.1 $status Synthetic\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    client.getOutputStream().apply{write(prefix.toByteArray());write(bytes);flush()}
                }
            }
        }}
        fun call(path:String,token:String):String {
            val connection=URL(base+path).openConnection()as HttpURLConnection
            connection.connectTimeout=3000;connection.readTimeout=3000;connection.setRequestProperty("Authorization","Bearer $token")
            return try {
                val code=connection.responseCode
                val text=(if(code>=400)connection.errorStream else connection.inputStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
                if(code>=400)throw ApiError.Http(HttpFailure(code,text))
                text
            }finally{connection.disconnect()}
        }
        override fun close(){socket.close();worker.shutdownNow();worker.awaitTermination(3,TimeUnit.SECONDS)}
    }
    private suspend fun cancelled(block:suspend()->Unit){try{block();fail("Stale HTTP request survived")}catch(_:CancellationException){}}
    private fun barrier(latch:CountDownLatch){check(latch.await(3,TimeUnit.SECONDS)){"Synthetic peer timed out"}}

    @Test fun `HTTP unauthorized response cannot retry with a replacement account token`() = runBlocking {
        val epoch=AtomicLong();val gate=RequestContext(epoch::get){epoch.incrementAndGet()};val context=gate.capture()
        var token="synthetic-token-a";var refreshes=0
        val arrived=CountDownLatch(1);val release=CountDownLatch(1)
        Peer {arrived.countDown();barrier(release);401 to "{}"}.use {old ->
            Peer {200 to "{}"}.use {replacement ->
                val request=async(Dispatchers.IO){RequestAuthorization(context,{token},{false},{refreshes++;ApiClient.RefreshOutcome.REFRESHED},{fail("Stale sign out")},{old.call("/api/users/synthetic-a/watchlist",it)}).execute()}
                try {
                    barrier(arrived);gate.changeAuthentication(change={token="synthetic-token-b";true});release.countDown()
                    cancelled{request.await()}
                    assertEquals(0,refreshes);assertEquals(listOf("Bearer synthetic-token-a"),old.requests.map{it.authorization});assertTrue(replacement.requests.isEmpty())
                }finally{release.countDown()}
            }
        }
    }
    @Test fun `a real HTTP retry uses only the same account rotated token`() = runBlocking {
        val gate=RequestContext({0L},{});val context=gate.capture();var token="synthetic-token-a";var calls=0
        Peer {calls++;if(calls==1)401 to "{}" else 200 to "{\"saved\":true}"}.use {peer ->
            val result=RequestAuthorization(context,{token},{false},{context.use{token="synthetic-rotated-a"};ApiClient.RefreshOutcome.REFRESHED},{fail("Unexpected sign out")},{peer.call("/api/users/synthetic-a/watchlist",it)}).execute()
            assertEquals("{\"saved\":true}",result);assertEquals(listOf("Bearer synthetic-token-a","Bearer synthetic-rotated-a"),peer.requests.map{it.authorization})
        }
    }
    @Test fun `an old HTTP refresh reply cannot replace newer account state`() = runBlocking {
        val epoch=AtomicLong();val gate=RequestContext(epoch::get){epoch.incrementAndGet()};val context=gate.capture();val rotation=gate.captureAuthentication()
        var token="synthetic-token-a";var saved=0
        val arrived=CountDownLatch(1);val release=CountDownLatch(1)
        Peer {arrived.countDown();barrier(release);200 to "{\"token\":\"synthetic-rotated-a\"}"}.use {old ->
            val request=async(Dispatchers.IO){RequestAuthorization(context,{token},{true},{
                val captured=rotation.use{token};old.call("/api/auth/refresh",captured)
                rotation.use{token="synthetic-rotated-a";saved++};ApiClient.RefreshOutcome.REFRESHED
            },{fail("Stale sign out")},{fail("Unexpected source request");""}).execute()}
            try {
                barrier(arrived);gate.changeAuthentication(change={token="synthetic-token-b";true});release.countDown()
                cancelled{request.await()};assertEquals("synthetic-token-b",token);assertEquals(0,saved);assertEquals(listOf("/api/auth/refresh"),old.requests.map{it.path})
            }finally{release.countDown()}
        }
    }
}
