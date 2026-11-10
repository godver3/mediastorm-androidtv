package com.strmr.tv.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiscreenMemoryTest {
    private fun channel(id: String, source: String = "s1") = LiveChannel(
        id = id, name = "Channel $id", url = "http://provider/$id", logo = "http://logo/$id.png", group = "Sports",
        tvgId = "tvg.$id", tvgName = "TVG $id", sourceId = source, sourceName = "Source", playbackId = "p$id",
    )

    private fun slot(channel: LiveChannel?) = MultiscreenSlot(channel, channel?.let { TileSource(it.url) })

    @Test
    fun savesOccupiedScreensInSlotOrderWithTheAudibleOne() {
        val state = MultiscreenSession.State(listOf(slot(channel("a")), slot(null), slot(channel("c")), slot(channel("d"))),
                                             audioIndex = 3, focusedIndex = 0)
        val saved = MultiscreenMemory.selection(state)!!
        assertEquals(listOf("a", "c", "d"), saved.channels.map { it.id })
        assertEquals(2, saved.audioIndex)
    }

    @Test
    fun failedScreensKeepTheirChannel() {
        val failed = MultiscreenSlot(channel("b"), error = MultiscreenSession.TUNE_FAILED)
        val state = MultiscreenSession.State(listOf(slot(channel("a")), failed), audioIndex = 0, focusedIndex = 1)
        assertEquals(listOf("a", "b"), MultiscreenMemory.selection(state)!!.channels.map { it.id })
    }

    @Test
    fun underTwoChannelsSavesNothing() {
        assertNull(MultiscreenMemory.selection(MultiscreenSession.State(listOf(slot(channel("a")), slot(null)), 0, 0)))
        assertNull(MultiscreenMemory.selection(MultiscreenSession.State(listOf(slot(null), slot(null)), 0, 0)))
    }

    @Test
    fun audibleScreenWithoutChannelFallsBackToTheFirst() {
        val state = MultiscreenSession.State(listOf(slot(null), slot(channel("b")), slot(channel("c"))), audioIndex = 0, focusedIndex = 0)
        assertEquals(0, MultiscreenMemory.selection(state)!!.audioIndex)
    }

    @Test
    fun roundTripsEveryChannelField() {
        val saved = SavedMultiscreen(listOf(channel("a"), channel("b", source = "s2"), channel("c")), audioIndex = 1)
        assertEquals(saved, MultiscreenMemory.decode(MultiscreenMemory.encode(saved)))
    }

    @Test
    fun keepsChannelsWithoutOptionalFields() {
        val bare = SavedMultiscreen(listOf(LiveChannel("a", "A", "http://a"), LiveChannel("b", "B", "http://b")))
        assertEquals(bare, MultiscreenMemory.decode(MultiscreenMemory.encode(bare)))
    }

    @Test
    fun rejectsJunkAndShortSelections() {
        assertNull(MultiscreenMemory.decode(null))
        assertNull(MultiscreenMemory.decode("not json"))
        assertNull(MultiscreenMemory.decode(MultiscreenMemory.encode(SavedMultiscreen(listOf(channel("a"))))))
    }

    @Test
    fun clampsAnOutOfRangeAudioIndex() {
        val text = MultiscreenMemory.encode(SavedMultiscreen(listOf(channel("a"), channel("b")), audioIndex = 7))
        assertEquals(1, MultiscreenMemory.decode(text)!!.audioIndex)
    }

    @Test
    fun ignoresUnknownKeys() {
        val text = """{"channels":[{"id":"a","name":"A","url":"u","extra":1},{"id":"b","name":"B","url":"v"}],"audioIndex":1,"v":2}"""
        assertEquals(listOf("a", "b"), MultiscreenMemory.decode(text)!!.channels.map { it.id })
    }

    @Test
    fun warningShowsOnceBeforeAThirdScreen() {
        assertFalse(MultiscreenMemory.needsWarning(occupied = 1, shownBefore = false))
        assertTrue(MultiscreenMemory.needsWarning(occupied = 2, shownBefore = false))
        assertTrue(MultiscreenMemory.needsWarning(occupied = 3, shownBefore = false))
        assertFalse(MultiscreenMemory.needsWarning(occupied = 2, shownBefore = true))
        assertFalse(MultiscreenMemory.needsWarning(occupied = 3, shownBefore = true))
    }
}
