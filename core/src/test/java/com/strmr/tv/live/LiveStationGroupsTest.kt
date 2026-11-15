package com.strmr.tv.live

import com.strmr.tv.api.ApiJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class LiveStationGroupsTest {
    private fun id(n:Int)="station:"+n.toString(16).padStart(24,'0')
    private fun channel(key:String,station:Int?=1,name:String="RTL HD",source:String="source-a")=LiveChannel(key,name,"http://127.0.0.1/synthetic/$key",sourceId=source,sourceName="Synthetic source",stationId=station?.let(::id),stationName=if(station!=null)"RTL" else null)
    private fun channels(rows:List<LiveStationGroups.Row>)=rows.filterIsInstance<LiveStationGroups.Channel>().map{it.channel}

    @Test fun `authoritative identities collect all loaded variants without changing the source entries`() {
        val first=channel("a");val second=channel("b",source="source-b");val other=channel("c",2,"RTLZWEI")
        val rows=LiveStationGroups.rows(listOf(first,other,second),true)
        assertEquals(listOf(first,second,other),channels(rows));val header=rows.filterIsInstance<LiveStationGroups.Header>().single()
        assertEquals("RTL",header.name);assertEquals(2,header.loadedSources);assertTrue(header.expanded)
        LiveStationReference.export()
    }
    @Test fun `identical names with different server identities are never merged`() {
        val entries=(1..5).map{channel("entry-$it",it,"RTL HD")}
        assertEquals(entries,channels(LiveStationGroups.rows(entries,true)));assertTrue(LiveStationGroups.rows(entries,true).none{it is LiveStationGroups.Header})
    }
    @Test fun `old servers retain a flat list without name guessing`() {
        val entries=listOf(channel("a",null),channel("b",null))
        val decoded=ApiJson.decodeFromString<LiveChannel>("""{"id":"legacy","name":"RTL HD","url":"http://127.0.0.1/synthetic"}""")
        assertNull(decoded.stationId);assertEquals(0,decoded.stationVariants)
        assertEquals(entries,channels(LiveStationGroups.rows(entries,true)));assertEquals(2,LiveStationGroups.rows(entries,true).size)
    }
    @Test fun `malformed or URL shaped station coordinates cannot group entries`() {
        for(coordinate in listOf("RTL","station:abc","http://127.0.0.1/synthetic","station:"+"g".repeat(24),"x".repeat(300))){
            val entries=listOf(channel("a").copy(stationId=coordinate),channel("b").copy(stationId=coordinate))
            assertEquals(2,LiveStationGroups.rows(entries,true).size)
        }
    }
    @Test fun `collapse preserves the first actual source and reopens all variants`() {
        val entries=listOf(channel("a"),channel("b",source="source-b"),channel("c",2))
        val collapsed=LiveStationGroups.toggle(emptySet(),id(1));assertEquals(listOf(entries[0],entries[2]),channels(LiveStationGroups.rows(entries,true,collapsed)))
        val reopened=LiveStationGroups.toggle(collapsed,id(1));assertEquals(entries,channels(LiveStationGroups.rows(entries,true,reopened)))
    }
    @Test fun `flat mode preserves original ordering and exposes collapsed sources`() {
        val entries=listOf(channel("a"),channel("c",2),channel("b",source="source-b"))
        assertEquals(entries,channels(LiveStationGroups.rows(entries,false,setOf(id(1)))))
    }
    @Test fun `counts describe loaded entries rather than the server total`() {
        val entries=listOf(channel("a").copy(stationVariants=9),channel("b").copy(stationVariants=9))
        val header=LiveStationGroups.rows(entries,true).filterIsInstance<LiveStationGroups.Header>().single()
        assertEquals(2,header.loadedSources)
    }
    @Test fun `header and entry keys stay stable when another page arrives`() {
        val first=channel("a");val second=channel("b");val third=channel("c",2)
        val old=LiveStationGroups.rows(listOf(first,second),true);val fresh=LiveStationGroups.rows(listOf(first,second,third),true)
        assertEquals(old.map{it.key},fresh.take(old.size).map{it.key});assertTrue(fresh.none{it.key.contains("http")})
    }
    @Test fun `same provider local id in separate sources remains independently selectable`() {
        val entries=listOf(channel("same",source="source-a"),channel("same",source="source-b"))
        val rows=LiveStationGroups.rows(entries,true);assertEquals(2,channels(rows).size);assertEquals(rows.size,rows.map{it.key}.toSet().size)
    }
    @Test fun `duplicate entries cannot duplicate lazy item keys`() {
        val first=channel("a");val rows=LiveStationGroups.rows(listOf(first,first),true)
        assertEquals(listOf(first),channels(rows));assertEquals(1,rows.size)
    }
    @Test fun `collapse state is bounded and ignores unverified keys`() {
        var collapsed=emptySet<String>();for(n in 1..300)collapsed=LiveStationGroups.toggle(collapsed,id(n))
        assertEquals(128,collapsed.size);assertFalse(id(1)in collapsed);assertTrue(id(300)in collapsed)
        assertEquals(collapsed,LiveStationGroups.toggle(collapsed,"RTL"))
    }
    @Test fun `station labels prefer nonempty server labels with a safe title fallback`() {
        val rows=LiveStationGroups.rows(listOf(channel("a").copy(stationName=" "),channel("b").copy(stationName=null)),true)
        assertEquals("RTL HD",rows.filterIsInstance<LiveStationGroups.Header>().single().name)
    }
    @Test fun `same labels retain separate server regions and display their hints`() {
        val entries=listOf(channel("a",1).copy(tvgCountry="DE"),channel("b",1).copy(tvgCountry="DE"),
            channel("c",2).copy(tvgCountry="AT"),channel("d",2).copy(tvgCountry="AT"))
        val headers=LiveStationGroups.rows(entries,true).filterIsInstance<LiveStationGroups.Header>()
        assertEquals(listOf("RTL","RTL"),headers.map{it.name})
        assertEquals(listOf("DE","AT"),headers.map{it.scopeHint})
        assertEquals(listOf(id(1),id(2)),headers.map{it.stationId})
    }
    @Test fun `language hints remain provider metadata without forcing a preferred language`() {
        val entries=listOf(channel("a").copy(tvgCountry=" FR ",tvgLanguage="fr"),
            channel("b").copy(tvgCountry="FR",tvgLanguage="en"))
        val header=LiveStationGroups.rows(entries,true).filterIsInstance<LiveStationGroups.Header>().single()
        assertEquals("FR · fr · en",header.scopeHint)
        assertEquals(entries,channels(LiveStationGroups.rows(entries,true)))
    }

}
