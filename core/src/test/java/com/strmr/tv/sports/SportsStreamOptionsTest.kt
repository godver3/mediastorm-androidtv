package com.strmr.tv.sports

import com.strmr.tv.live.LiveStremioStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sports stream picker's Stremio stream rows (Expo `renderOptions`) and its auto-play rule. */
class SportsStreamOptionsTest {
    private val addonUrl = "https://addon.example/stream/tv/espn.json"
    private val addon = SportsStreamMatch(
        channelId = "espn", channelName = "ESPN", channelUrl = addonUrl, sourceId = "stremio", sourceName = "Addon",
        confidence = 0.9, confidenceTier = "strong",
    )
    private val iptv = SportsStreamMatch(channelId = "tsn", channelName = "TSN", channelUrl = "http://iptv/tsn.ts", confidenceTier = "possible")

    private fun stream(index: Int, label: String = "", title: String? = null, name: String? = null) =
        LiveStremioStream(index = index, label = label, title = title, name = name)

    @Test fun expandsAStremioChannelInTheAddonsOrder() {
        val lookups = mapOf(addonUrl to SportsStreamLookup.Found(listOf(stream(2, "1080p"), stream(0, title = "720p"), stream(1))))
        val rows = SportsStreamOptions.rows(listOf(addon, iptv), lookups)
        assertEquals(listOf("ESPN · 1080p", "ESPN · 720p", "ESPN · Feed 2", "TSN"), rows.map { it.title })
        // The row leads with the stream (what tells them apart); the channel joins the details.
        assertEquals(listOf("1080p", "720p", "Feed 2", "TSN"), rows.map { it.rowTitle })
        assertEquals("Addon · ESPN · 90%", com.strmr.tv.ui.sports.SportsStreamSearch.details(rows[0]))
        assertEquals("", com.strmr.tv.ui.sports.SportsStreamSearch.details(rows[3]))
        assertEquals(listOf(2, 0, 1, null), rows.map { it.stremioStreamIndex })
        // The match's source, confidence and badge carry over; the index reaches the tuned channel.
        assertTrue(rows.take(3).all { it.isStrong && it.sourceName == "Addon" && it.confidence == 0.9 })
        assertEquals(2, rows.first().channel.stremioStreamIndex)
        assertEquals("ESPN", rows.first().channel.name)
        // The first stream keeps the channel row's key, so focus on it stays put.
        assertEquals(addon.key, rows.first().key)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test fun keepsThePlainRowWhileLookingUpAndOnFailure() {
        val pending = SportsStreamOptions.rows(listOf(addon), mapOf(addonUrl to SportsStreamLookup.Pending)).single()
        assertTrue(pending.loadingStreams)
        assertNull(pending.stremioStreamIndex)
        for (lookup in listOf(SportsStreamLookup.Failed, SportsStreamLookup.Found(emptyList()))) {
            val row = SportsStreamOptions.rows(listOf(addon), mapOf(addonUrl to lookup)).single()
            assertEquals("ESPN", row.title)
            assertNull(row.stremioStreamIndex)
            assertTrue(!row.loadingStreams)
        }
        // A channel that isn't a Stremio addon is never looked up.
        assertEquals(listOf(addonUrl), SportsStreamOptions.lookupUrls(listOf(addon, iptv, addon.copy(channelId = "espn2"))))
    }

    @Test fun dropsRepeatedSourceUrlAndStream() {
        val twin = addon.copy(channelId = "espn-alt", channelName = "ESPN Alt")
        val lookups = mapOf(addonUrl to SportsStreamLookup.Found(listOf(stream(0, "A"), stream(1, "B"))))
        val rows = SportsStreamOptions.rows(listOf(addon, twin), lookups)
        assertEquals(listOf("ESPN · A", "ESPN · B"), rows.map { it.title })
        assertEquals(listOf(0, 3), SportsStreamOptions.clean(listOf(stream(0), stream(-1), stream(0, "dupe"), stream(3))).map { it.index })
    }

    @Test fun autoPlaysOnlyAClearAnswer() {
        val group = SportsStreamGroup("strong", addon)
        assertEquals(addon, SportsStreamOptions.autoPlayCandidate(listOf(group), broad = false, live = true))
        assertNull(SportsStreamOptions.autoPlayCandidate(listOf(group), broad = true, live = true))
        assertNull(SportsStreamOptions.autoPlayCandidate(listOf(group), broad = false, live = false))
        assertNull(SportsStreamOptions.autoPlayCandidate(listOf(group.copy(alternatives = listOf(iptv))), broad = false, live = true))
        assertNull(SportsStreamOptions.autoPlayCandidate(listOf(group, group), broad = false, live = true))

        assertEquals(SportsAutoPlay.Show, SportsStreamOptions.autoPlay(null, emptyMap()))
        assertEquals(SportsAutoPlay.Play(iptv), SportsStreamOptions.autoPlay(iptv, emptyMap()))
        assertEquals(SportsAutoPlay.Wait, SportsStreamOptions.autoPlay(addon, mapOf(addonUrl to SportsStreamLookup.Pending)))
        assertEquals(SportsAutoPlay.Play(addon), SportsStreamOptions.autoPlay(addon, mapOf(addonUrl to SportsStreamLookup.Failed)))
        val one = SportsStreamOptions.autoPlay(addon, mapOf(addonUrl to SportsStreamLookup.Found(listOf(stream(4, "HD")))))
        assertEquals(4, (one as SportsAutoPlay.Play).match.channel.stremioStreamIndex)
        val several = SportsStreamOptions.autoPlay(addon, mapOf(addonUrl to SportsStreamLookup.Found(listOf(stream(0), stream(1)))))
        assertEquals(SportsAutoPlay.Show, several)
    }
}
