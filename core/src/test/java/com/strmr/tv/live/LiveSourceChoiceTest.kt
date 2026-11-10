package com.strmr.tv.live

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSourceChoiceTest {
    private fun channel(url: String) = LiveChannel(id = "s:1", name = "Sky Sports", url = url, sourceId = "s")

    @Test fun onlyStremioStreamResourcesOfferSources() {
        assertTrue(channel("https://addon.example/stream/tv/sky.json").hasStremioSources)
        assertTrue(channel("https://addon.example/cfg/stream/tv/sky.json?x=1").hasStremioSources)
        assertFalse(channel("https://provider.example/live/u/p/123.ts").hasStremioSources)
        assertFalse(channel("https://addon.example/catalog/tv/all.json").hasStremioSources)
        assertFalse(channel("stremio://addon.example/stream/tv/sky.json").hasStremioSources)
    }

    @Test fun decodesChannelsWithoutAPickAndStreams() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<LiveChannel>("""{"id":"1","name":"A","url":"https://a/stream/tv/a.json"}""")
        assertNull(decoded.stremioStreamIndex)
        val streams = json.decodeFromString<List<LiveStremioStream>>("""[{"index":0,"label":"HD"},{"index":3,"label":"FHD","name":"Addon"}]""")
        assertEquals(listOf(0, 3), streams.map { it.index })
        assertEquals(3, decoded.copy(stremioStreamIndex = streams[1].index).stremioStreamIndex)
    }
}
