package com.strmr.tv.home

import com.strmr.tv.api.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeSourceObserverTest {
    private val target = DetailsTarget("tmdb:series:123", "Synthetic Series", false)
    private fun ep(number: Int) = Episode("ep-$number", "Synthetic episode", seasonNumber = 1, episodeNumber = number, numbering = EpisodeNumbering("tvdb:123", "official"))
    private fun source(state: String = "found") = SourceAvailability(state, Instant.now().minusSeconds(60).toString(), Instant.now().plusSeconds(3600).toString(), complete = true, reason = if (state == "found") "matching_candidates" else "no_matching_candidates")
    private inner class Gateway : EpisodeSourceGateway {
        var current = true
        var enabled = true
        val requests = mutableListOf<Pair<String, EpisodeSourceDemand>>()
        var intercept: (suspend (EpisodeSourceDemand) -> EpisodeSourceResponse?)? = null
        override fun isCurrent() = current
        override suspend fun update(view: String, body: EpisodeSourceDemand): EpisodeSourceResponse {
            requests += view to body
            intercept?.invoke(body)?.let { return it }
            return EpisodeSourceResponse(enabled, body.version, body.episodes.map { EpisodeSourceItem(it.episodeId, it.seasonNumber, it.episodeNumber, source()) })
        }
    }
    private fun observer(gateway: Gateway, target: DetailsTarget = this.target) = EpisodeSourceObserver(gateway, target, "synthetic-view")

    @Test fun visibleEpisodesAreBatchedAndCleanupIsOrdered() = runTest {
        val gateway = Gateway()
        val values = observer(gateway).states(listOf(ep(1), ep(2))).take(2).toList()
        assertTrue(values.first().isEmpty())
        assertEquals(2, values.last().size)
        assertEquals(2, gateway.requests.first().second.episodes.size)
        assertTrue(gateway.requests.last().second.episodes.isEmpty())
        assertTrue(gateway.requests.last().second.version > gateway.requests.first().second.version)
        assertEquals("tmdb:series:123", gateway.requests.first().second.titleId)
    }
    @Test fun duplicateAndExcessVisibleItemsAreBounded() = runTest {
        val gateway = Gateway(); observer(gateway).states((1..20).map(::ep) + ep(1)).take(2).toList()
        assertEquals(12, gateway.requests.first().second.episodes.size)
        assertEquals(12, gateway.requests.first().second.episodes.map { it.episodeId }.distinct().size)
    }
    @Test fun movieEmptyAndUnnumberedViewsDoNotRequestSources() = runTest {
        val gateway = Gateway()
        observer(gateway).states(emptyList()).toList()
        observer(gateway).states(listOf(ep(1).copy(numbering = null))).toList()
        observer(gateway, target.copy(isMovie = true)).states(listOf(ep(1))).toList()
        assertTrue(gateway.requests.isEmpty())
    }
    @Test fun mixedNumberingIsNotSentAsOneView() = runTest {
        val gateway = Gateway()
        observer(gateway).states(listOf(ep(1), ep(2).copy(numbering = EpisodeNumbering("tvdb:123", "dvd")))).toList()
        assertTrue(gateway.requests.isEmpty())
    }
    @Test fun serverDisabledHintsStopWithoutRenewals() = runTest {
        val gateway = Gateway(); gateway.enabled = false
        val states = observer(gateway).states(listOf(ep(1))).toList()
        assertEquals(1, states.size); assertEquals(1, gateway.requests.size)
    }
    @Test fun staleResponseVersionCannotPopulateCards() = runTest {
        val gateway = Gateway(); gateway.intercept = { body -> if (body.episodes.isNotEmpty()) EpisodeSourceResponse(true, body.version - 1) else null }
        assertEquals(listOf(emptyMap<String, SourceAvailability>()), observer(gateway).states(listOf(ep(1))).toList())
    }
    @Test fun responseMustMatchEpisodeCoordinatesExactly() = runTest {
        val gateway = Gateway(); gateway.intercept = { body -> if (body.episodes.isNotEmpty()) EpisodeSourceResponse(true, body.version, listOf(EpisodeSourceItem("ep-1", 2, 1, source()))) else null }
        val states = observer(gateway).states(listOf(ep(1))).take(2).toList()
        assertTrue(states.last().isEmpty())
    }
    @Test fun duplicateResponseRowsRemainUnknown() = runTest {
        val gateway = Gateway(); gateway.intercept = { body -> if (body.episodes.isNotEmpty()) EpisodeSourceResponse(true, body.version, List(2) { EpisodeSourceItem("ep-1", 1, 1, source()) }) else null }
        assertTrue(observer(gateway).states(listOf(ep(1))).take(2).toList().last().isEmpty())
    }
    @Test fun completedHintsRenewBeforeTheServerLeaseExpires() = runTest {
        val gateway = Gateway(); val values = mutableListOf<Map<String, SourceAvailability>>()
        val job = launch { observer(gateway).states(listOf(ep(1))).collect { values += it } }
        runCurrent(); assertEquals(1, gateway.requests.size)
        advanceTimeBy(15_000); runCurrent(); assertEquals(2, gateway.requests.size)
        job.cancel(); runCurrent(); assertTrue(gateway.requests.last().second.episodes.isEmpty())
    }
    @Test fun pendingHintsPollBrieflyWithoutAnotherViewId() = runTest {
        val gateway = Gateway(); gateway.intercept = { body -> if (body.episodes.isNotEmpty()) EpisodeSourceResponse(true, body.version, listOf(EpisodeSourceItem("ep-1", 1, 1, SourceAvailability(reason = "not_checked")))) else null }
        val job = launch { observer(gateway).states(listOf(ep(1))).collect {} }; runCurrent()
        advanceTimeBy(1_000); runCurrent(); assertEquals(2, gateway.requests.size)
        assertEquals(1, gateway.requests.map { it.first }.distinct().size)
        job.cancel(); runCurrent()
    }
    @Test fun profileChangeDiscardsReplyAndAvoidsCleanupUnderNewIdentity() = runTest {
        val gateway = Gateway(); gateway.intercept = { body -> if (body.episodes.isNotEmpty()) { gateway.current = false; EpisodeSourceResponse(true, body.version) } else null }
        assertTrue(observer(gateway).states(listOf(ep(1))).toList().all { it.isEmpty() })
        assertEquals(1, gateway.requests.size)
    }
    @Test fun failedRequestsClearTheViewAndLeaveCardsUnknown() = runTest {
        val gateway = Gateway(); gateway.intercept = { body -> if (body.episodes.isNotEmpty()) throw IOException("synthetic failure") else null }
        assertTrue(observer(gateway).states(listOf(ep(1))).toList().all { it.isEmpty() })
        assertTrue(gateway.requests.last().second.episodes.isEmpty())
    }
    @Test fun lateOldResponseCannotClearANewerVisibleBatch() = runTest {
        val gateway = Gateway(); val gate = CompletableDeferred<Unit>()
        gateway.intercept = { body -> if (body.version == 1L) withContext(NonCancellable) { gate.await(); EpisodeSourceResponse(true, body.version) } else null }
        val observer = observer(gateway)
        val old = launch { observer.states(listOf(ep(1))).collect {} }; runCurrent()
        val newer = launch { observer.states(listOf(ep(2))).collect {} }; runCurrent()
        old.cancel(); gate.complete(Unit); runCurrent()
        assertTrue(gateway.requests.none { it.second.episodes.isEmpty() })
        newer.cancel(); runCurrent(); assertTrue(gateway.requests.last().second.episodes.isEmpty())
    }
    @Test fun alreadySentOldCleanupHasAnEarlierVersionThanTheNewBatch() = runTest {
        val gateway = Gateway(); val gate = CompletableDeferred<Unit>()
        gateway.intercept = { body -> if (body.episodes.isEmpty() && body.version == 2L) { gate.await(); EpisodeSourceResponse(true, body.version) } else null }
        val observer = observer(gateway); val old = launch { observer.states(listOf(ep(1))).collect {} }; runCurrent()
        old.cancel(); runCurrent()
        val newer = launch { observer.states(listOf(ep(2))).collect {} }; runCurrent()
        val clear = gateway.requests.first { it.second.episodes.isEmpty() }.second.version
        val fresh = gateway.requests.last { it.second.episodes.isNotEmpty() }.second.version
        assertTrue(clear < fresh)
        gate.complete(Unit); runCurrent(); newer.cancel(); runCurrent()
    }
    @Test fun sequentialScrollBatchesReuseOneViewAndIncreaseVersion() = runTest {
        val gateway = Gateway(); val observer = observer(gateway)
        for (number in 1..25) observer.states(listOf(ep(number))).take(2).toList()
        assertEquals(1, gateway.requests.map { it.first }.distinct().size)
        assertEquals(gateway.requests.map { it.second.version }.sorted(), gateway.requests.map { it.second.version })
    }
    @Test fun localCopyPreventsAnUnsupportedTotalAbsenceClaim() {
        val missing = SourceHint.from(source("not_found"))!!
        assertTrue(missing.dimArtwork)
        val local = episodeSourcePresentation(missing, true)!!
        assertFalse(local.dimArtwork); assertEquals(SourceHint.Kind.UNKNOWN, local.kind)
        assertEquals("Local version needs checking", local.label)
        assertEquals(missing, episodeSourcePresentation(missing, false))
    }
    @Test fun sourceKeysIncludeNumberingAndCoordinates() {
        assertNotEquals(episodeSourceKey(ep(1)), episodeSourceKey(ep(2)))
        assertNotEquals(episodeSourceKey(ep(1)), episodeSourceKey(ep(1).copy(numbering = EpisodeNumbering("tvdb:123", "dvd"))))
    }
    @Test fun wireBodiesContainCoordinatesInsteadOfClientProvidedSearchLabels() {
        val body = EpisodeSourceDemand(1, target.titleId, ep(1).numbering, listOf(EpisodeSourceCoordinate("ep-1", 1, 1)))
        val encoded = Json.encodeToString(body)
        assertTrue(encoded.contains("seasonNumber")); assertFalse(encoded.contains("Synthetic Series")); assertFalse(encoded.contains("query"))
        assertEquals(body, Json.decodeFromString<EpisodeSourceDemand>(encoded))
    }
    @Test fun pendingChecksBackOffWhileStillRenewingTheirLease() = runTest {
        val gateway = Gateway()
        gateway.intercept = { body -> if (body.episodes.isNotEmpty()) EpisodeSourceResponse(true, body.version, listOf(EpisodeSourceItem("ep-1", 1, 1, SourceAvailability(reason = "not_checked")))) else null }
        val job = launch { observer(gateway).states(listOf(ep(1))).collect {} }; runCurrent()
        advanceTimeBy(25_000); runCurrent()
        assertEquals(16, gateway.requests.size)
        advanceTimeBy(5_000); runCurrent(); assertEquals(17, gateway.requests.size)
        job.cancel(); runCurrent()
    }

    @Test fun cleanupWireBodyKeepsTheRequiredEmptyEpisodeArray() {
        val body = EpisodeSourceDemand(2).toEpisodeSourceBody()
        assertEquals("[]", body["episodes"].toString())
        assertFalse(body.containsKey("numbering"))
        assertFalse(body.containsKey("titleId"))
        assertEquals("2", body["version"].toString())
    }

}
