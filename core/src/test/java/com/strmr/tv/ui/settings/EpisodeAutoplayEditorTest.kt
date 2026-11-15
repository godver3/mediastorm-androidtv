package com.strmr.tv.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EpisodeAutoplayEditorTest {
    private class Gateway : EpisodeAutoplayGateway {
        var current = true
        var value = true
        var reads = 0
        val writes = mutableListOf<Boolean>()
        var beforeRead: suspend () -> Unit = {}
        var afterWrite: suspend () -> Unit = {}
        var reply: Boolean? = null
        override fun isCurrent() = current
        override suspend fun read(): Boolean { reads++; beforeRead(); return value }
        override suspend fun write(value: Boolean): Boolean { writes += value; this.value = value; afterWrite(); return reply ?: value }
    }
    @Test fun `load does not write or invent an off preference`() = runTest {
        val g = Gateway(); val e = EpisodeAutoplayEditor(g) {}; assertNull(e.state.value.enabled)
        e.load(); assertEquals(true,e.state.value.enabled); assertTrue(g.writes.isEmpty())
    }
    @Test fun `failed read remains unavailable and retries without writing`() = runTest {
        val g = Gateway(); g.beforeRead = { throw IOException("private endpoint") }; val e = EpisodeAutoplayEditor(g) {}
        e.load(); e.save(false); assertNull(e.state.value.enabled); assertTrue(g.writes.isEmpty())
        assertFalse(e.state.value.error!!.contains("private")); g.beforeRead = {}; e.load(); assertEquals(true,e.state.value.enabled)
    }
    @Test fun `save waits for confirmation and reports one profile change`() = runTest {
        val g = Gateway(); val saved = mutableListOf<Boolean>(); val e = EpisodeAutoplayEditor(g,saved::add)
        e.load(); e.save(false); assertEquals(false,e.state.value.enabled); assertEquals(listOf(false),saved)
    }
    @Test fun `lost acknowledgement keeps confirmed value and retries the intended choice`() = runTest {
        val g = Gateway(); val saved = mutableListOf<Boolean>(); val e = EpisodeAutoplayEditor(g,saved::add); e.load()
        g.afterWrite = { throw IOException("private") }; e.save(false)
        assertFalse(g.value); assertEquals(true,e.state.value.enabled); assertTrue(saved.isEmpty())
        g.afterWrite = {}; e.save(false); assertEquals(false,e.state.value.enabled); assertEquals(listOf(false,false),g.writes)
    }
    @Test fun `late read and late write cannot cross profile boundaries`() = runTest {
        val read = Gateway(); val reader = EpisodeAutoplayEditor(read) {}; read.beforeRead = { read.current = false }
        try { reader.load(); fail("stale read") } catch (_: CancellationException) {}
        assertNull(reader.state.value.enabled)
        val write = Gateway(); var saved = 0; val editor = EpisodeAutoplayEditor(write) { saved++ }; editor.load()
        write.afterWrite = { write.current = false }
        try { editor.save(false); fail("stale write") } catch (_: CancellationException) {}
        assertEquals(true,editor.state.value.enabled); assertEquals(0,saved)
    }
    @Test fun `busy editor rejects duplicate and conflicting writes`() = runTest {
        val g = Gateway(); val gate = CompletableDeferred<Unit>(); g.afterWrite = { gate.await() }
        val e = EpisodeAutoplayEditor(g) {}; e.load(); val first = launch { e.save(false) }; runCurrent()
        e.save(false); e.save(true); assertEquals(listOf(false),g.writes); assertTrue(e.state.value.saving)
        gate.complete(Unit); first.join(); assertEquals(false,e.state.value.enabled)
    }
    @Test fun `closed editor rejects late confirmations`() = runTest {
        val g = Gateway(); var saved = 0; val e = EpisodeAutoplayEditor(g) { saved++ }; e.load(); g.afterWrite = { e.close() }
        try { e.save(false); fail("closed write") } catch (_: CancellationException) {}
        assertEquals(0,saved)
    }
    @Test fun `unexpected confirmation cannot change the selection`() = runTest {
        val g = Gateway(); g.reply = true; val e = EpisodeAutoplayEditor(g) {}; e.load(); e.save(false)
        assertEquals(true,e.state.value.enabled); assertNotNull(e.state.value.error)
    }
}
