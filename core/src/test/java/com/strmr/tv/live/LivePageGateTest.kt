package com.strmr.tv.live

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LivePageGateTest {
    @Test fun `one query permits one active page at a time`() {
        val gate=LivePageGate<String>{0L};val first=gate.begin("rtl")!!;assertNull(gate.begin("rtl"));assertTrue(gate.accepts(first,"rtl"));assertTrue(gate.finish(first));assertNotNull(gate.begin("rtl"))
    }
    @Test fun `forced reload discards the previous page even for the same query`() {
        val gate=LivePageGate<String>{0L};val old=gate.begin("rtl")!!;gate.reset();val current=gate.begin("rtl")!!
        assertFalse(gate.accepts(old,"rtl"));assertFalse(gate.finish(old));assertTrue(gate.accepts(current,"rtl"))
    }
    @Test fun `an A B A filter cycle cannot revive the first reply`() {
        val gate=LivePageGate<String>{0L};val old=gate.begin("a")!!;gate.reset();gate.begin("b");gate.reset();val current=gate.begin("a")!!
        assertFalse(gate.accepts(old,"a"));assertTrue(gate.accepts(current,"a"))
    }
    @Test fun `a completed old filter cannot clear the current loading state`() {
        val gate=LivePageGate<String>{0L};val old=gate.begin("a")!!;val current=gate.begin("b")!!
        assertFalse(gate.finish(old));assertTrue(gate.accepts(current,"b"))
    }
    @Test fun `identity changes discard old pages and prevent new requests from the old store`() {
        var epoch=0L;val gate=LivePageGate<String>{epoch};val old=gate.begin("a")!!;epoch++
        assertFalse(gate.accepts(old,"a"));try{gate.begin("a");fail("Old profile requested another page")}catch(_:CancellationException){}
    }
    @Test fun `tickets from another store cannot complete a matching query`() {
        val first=LivePageGate<String>{0L};val second=LivePageGate<String>{0L};val foreign=first.begin("a")!!;val current=second.begin("a")!!
        assertFalse(second.accepts(foreign,"a"));assertFalse(second.finish(foreign));assertTrue(second.accepts(current,"a"))
    }
    @Test fun `the next cursor consumes raw entries even when display entries are duplicates`() {
        val gate=LivePageGate<String>{0L};val first=gate.begin("a")!!;assertEquals(0,first.offset)
        assertTrue(gate.commitPage(first,"a",250));gate.finish(first);assertEquals(250,gate.begin("a")!!.offset)
    }
    @Test fun `forced reload resets the raw page cursor`() {
        val gate=LivePageGate<String>{0L};val first=gate.begin("a")!!;gate.commitPage(first,"a",250);gate.finish(first)
        gate.reset();assertEquals(0,gate.begin("a")!!.offset)
    }
    @Test fun `an old reply cannot advance a newer filter cursor`() {
        val gate=LivePageGate<String>{0L};val old=gate.begin("a")!!;gate.reset();val current=gate.begin("b")!!
        assertFalse(gate.commitPage(old,"a",250));assertTrue(gate.commitPage(current,"b",10));gate.finish(current);assertEquals(10,gate.begin("b")!!.offset)
    }
    @Test fun `favorite membership is part of the server page snapshot`() {
        val gate=LivePageGate<LiveStore.Query>{0L}
        val old=LiveStore.Query(LiveFilter.Favorites,null,"",favoriteIds=setOf("synthetic-a"))
        val current=old.copy(favoriteIds=setOf("synthetic-b"));val ticket=gate.begin(old)!!
        assertFalse(gate.accepts(ticket,current));assertEquals(0,gate.begin(current)!!.offset)
    }

}
