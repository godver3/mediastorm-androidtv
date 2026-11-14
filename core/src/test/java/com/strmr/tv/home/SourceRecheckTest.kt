package com.strmr.tv.home

import com.strmr.tv.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SourceRecheckTest {
    private val movie = DetailsTarget("tmdb:movie:1", "Synthetic private title", true)
    private val episode = Episode("ep-1", seasonNumber = 1, episodeNumber = 2, numbering = EpisodeNumbering("tvdb:2", "official"))
    private class Gateway : SourceRecheckGateway {
        var current = true
        var calls = 0
        var reply = SourceRecheckResponse("queued")
        var body: SourceRecheckRequest? = null
        var onRequest: () -> Unit = {}
        override fun isCurrent() = current
        override suspend fun request(body: SourceRecheckRequest): SourceRecheckResponse { calls++; this.body = body; onRequest(); return reply }
    }
    @Test fun movieSendsCoordinatesOnlyAndSignalsAcceptanceOnce() = runTest {
        val g = Gateway(); var accepted = 0
        requestSourceRecheck(g, movie, onAccepted = { accepted++ })
        assertEquals(1,accepted); assertEquals(1,g.calls)
        assertEquals("{\"titleId\":\"tmdb:movie:1\",\"mediaType\":\"movie\"}",g.body!!.toSourceRecheckBody().toString())
        assertFalse(g.body!!.toSourceRecheckBody().toString().contains(movie.name))
    }
    @Test fun episodePreservesExactNumberingWithoutCallerQuery() = runTest {
        val g = Gateway(); requestSourceRecheck(g, movie.copy(isMovie=false), episode, {})
        assertEquals(episode.numbering,g.body!!.numbering)
        assertEquals(EpisodeSourceCoordinate("ep-1",1,2),g.body!!.episode)
        val body=g.body!!.toSourceRecheckBody().toString(); assertTrue(body.contains("official"));assertFalse(body.contains("query"))
    }
    @Test fun wholeSeriesNeverInventsAnEpisode() = runTest {
        val g = Gateway();requestSourceRecheck(g,movie.copy(isMovie=false),onAccepted={})
        assertEquals("series",g.body!!.mediaType);assertNull(g.body!!.episode);assertNull(g.body!!.numbering)
    }
    @Test fun libraryUnknownAndUnnumberedEpisodeCannotStartWork() = runTest {
        for ((target,ep) in listOf(movie.copy(fromLibrary=true) to null,movie.copy(titleId="") to null,movie to episode,movie.copy(isMovie=false) to episode.copy(numbering=null))) {
            val g=Gateway();try {requestSourceRecheck(g,target,ep,{});fail("Invalid target accepted")}catch(_:IllegalArgumentException){};assertEquals(0,g.calls)
        }
    }
    @Test fun identityChangeBeforeRequestStartsNoWork() = runTest {
        val g=Gateway();g.current=false
        try {requestSourceRecheck(g,movie,onAccepted={fail("Stale identity accepted")});fail("Stale request accepted")}catch(_:CancellationException){}
        assertEquals(0,g.calls)
    }
    @Test fun lateReplyAfterProfileOrServerSwitchCannotSignalAcceptance() = runTest {
        val g=Gateway();g.onRequest={g.current=false}
        try {requestSourceRecheck(g,movie,onAccepted={fail("Old response signaled")});fail("Late reply accepted")}catch(_:CancellationException){}
        assertEquals(1,g.calls)
    }
    @Test fun unexpectedResponseCannotSignalAcceptance() = runTest {
        val g=Gateway();g.reply=SourceRecheckResponse("completed")
        try {requestSourceRecheck(g,movie,onAccepted={fail("Unexpected reply accepted")});fail("Unexpected reply accepted")}catch(_:IllegalStateException){}
    }
    @Test fun failureDoesNotStartAPollOrHideError() = runTest {
        val g=Gateway();g.onRequest={throw IOException("synthetic outage")}
        try {requestSourceRecheck(g,movie,onAccepted={fail("Failure signaled")});fail("Failure hidden")}catch(_:IOException){}
        assertEquals(1,g.calls)
    }
}
