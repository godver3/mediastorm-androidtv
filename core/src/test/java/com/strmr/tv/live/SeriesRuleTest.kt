package com.strmr.tv.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesRuleTest {
    private fun channel(id: String, tvg: String? = null) = LiveChannel(id = id, name = "Channel $id", url = "https://e/$id.m3u8", tvgId = tvg)

    @Test fun titleRuleMatchesNormalizedTitleOnItsChannelOnly() {
        val rule = SeriesRule(id = "r", pattern = "The Simpsons", channelId = "ch1")
        assertTrue(rule.covers("the simpsons", channel("ch1")))
        assertTrue(rule.covers("The  Simpsons!", channel("CH1")))
        assertFalse(rule.covers("The Simpsons", channel("ch2")))
        assertFalse(rule.covers("The Simpsons Movie", channel("ch1")))
        assertTrue(SeriesRule(id = "r", pattern = "News", tvgId = "cnn.us").covers("News", channel("other", "CNN.us")))
    }

    @Test fun allChannelsAndRegexRules() {
        assertTrue(SeriesRule(id = "r", pattern = "Jeopardy", allChannels = true).covers("JEOPARDY", channel("ch9")))
        val regex = SeriesRule(id = "r", matchType = "regex", pattern = "(news|weather)", allChannels = true)
        assertTrue(regex.covers("Evening NEWS", channel("ch3")))
        assertFalse(regex.covers("Cartoons", channel("ch3")))
        assertFalse(SeriesRule(id = "r", matchType = "regex", pattern = "(", allChannels = true).covers("(", channel("ch3")))
    }

    @Test fun findPrefersTitleRuleOverRegex() {
        val regex = SeriesRule(id = "a", matchType = "regex", pattern = "news", allChannels = true)
        val title = SeriesRule(id = "b", pattern = "News", channelId = "ch1")
        assertEquals("b", SeriesRule.find(listOf(regex, title), "News", channel("ch1"))?.id)
    }

    @Test fun normalizedTitleMatchesServer() {
        assertEquals("law order svu", SeriesRule.normalizedTitle("  Law & Order: SVU "))
        assertEquals("café 24 7", SeriesRule.normalizedTitle("Café 24/7"))
    }
}
