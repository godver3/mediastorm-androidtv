package com.strmr.tv.taste

import com.strmr.tv.api.Title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TasteSetupOfferTest {
    private class Gateway : TasteSetupGateway {
        var current = true
        var reply = TasteSetupReply("new", available = true)
        var calls = 0
        var onRead: () -> Unit = {}
        override fun isCurrent() = current
        override suspend fun read(): TasteSetupReply { calls++; onRead(); return reply }
        override suspend fun candidates(query: String, movie: Boolean) = emptyList<Title>()
        override suspend fun save(action: String, titles: List<Title>) = error("Offer must never save data")
    }
    @Test fun onlyAnAvailableNewProfileGetsOneOptionalOffer() = runTest {
        for (status in listOf("new", "skipped", "completed")) {
            val g = Gateway(); g.reply = g.reply.copy(status = status); val offer = TasteSetupOffer(); var shown = 0
            offer.check(g, { true }) { shown++ }; offer.check(g, { true }) { shown++ }
            assertEquals(if (status == "new") 1 else 0, shown); assertEquals(1, g.calls)
        }
    }
    @Test fun unavailableProfileAndHiddenHomeDoNotInterruptViewing() = runTest {
        val g = Gateway(); val offer = TasteSetupOffer(); var shown = 0
        offer.check(g, { false }) { shown++ }; assertEquals(0, g.calls)
        g.reply = g.reply.copy(available = false); offer.check(g, { true }) { shown++ }; assertEquals(0, shown)
    }
    @Test fun movingToPlaybackBeforeReplyDefersUntilHomeIsVisibleAgain() = runTest {
        val g = Gateway(); val offer = TasteSetupOffer(); var visible = true; var shown = 0
        g.onRead = { visible = false }; offer.check(g, { visible }) { shown++ }; assertEquals(0, shown)
        visible = true; g.onRead = {}; offer.check(g, { visible }) { shown++ }; assertEquals(1, shown)
    }
    @Test fun optionalNetworkFailureDoesNotBlockOrRepeatedlyNag() = runTest {
        val g = Gateway(); val offer = TasteSetupOffer(); g.onRead = { throw IOException("private diagnostic") }; var shown = 0
        offer.check(g, { true }) { shown++ }; offer.check(g, { true }) { shown++ }
        assertEquals(1, g.calls); assertEquals(0, shown)
    }
    @Test fun cancelledHomeRequestCanRetryButStaleProfileNeverShows() = runTest {
        val g = Gateway(); val offer = TasteSetupOffer(); g.onRead = { throw CancellationException() }; var shown = 0
        try { offer.check(g, { true }) { shown++ }; fail("Cancellation hidden") } catch (_: CancellationException) {}
        g.onRead = { g.current = false }; offer.check(g, { true }) { shown++ }; assertEquals(0, shown)
    }
}
