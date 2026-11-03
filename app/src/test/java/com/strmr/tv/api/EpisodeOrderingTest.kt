package com.strmr.tv.api

import com.strmr.tv.player.PlaybackRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alternate episode orderings: the server's ordering list and per-episode numbering decode, and
 * an episode's ordering keeps prequeues for "S01E02" in different orderings apart.
 */
class EpisodeOrderingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun decodesOrderingsAndEpisodeNumbering() {
        val details = json.decodeFromString<SeriesDetails>(
            """
            {"title":{"id":"tvdb:series:74852","name":"Avatar","mediaType":"series"},
             "availableOrderings":[{"type":"official","name":"Aired Order","seasonCount":3,"isOfficial":true},
                                   {"type":"dvd","name":"DVD Order","seasonCount":3,"isOfficial":false}],
             "activeOrdering":"dvd",
             "seasons":[{"id":"s1","name":"Book One","number":1,"episodes":[
                {"id":"e1","name":"The Boy in the Iceberg","seasonNumber":1,"episodeNumber":1,
                 "numbering":{"seriesId":"tvdb:series:74852","ordering":"dvd"}}]}]}
            """,
        )
        assertEquals(listOf("official", "dvd"), details.availableOrderings.map { it.type })
        assertTrue(details.availableOrderings.first().isOfficial)
        assertEquals("dvd", details.activeOrdering)
        assertEquals(EpisodeNumbering("tvdb:series:74852", "dvd"), details.seasons.first().episodes.first().numbering)
    }

    @Test fun decodesWithoutOrderings() {
        val details = json.decodeFromString<SeriesDetails>(
            """
            {"title":{"id":"tmdb:tv:1","name":"Show","mediaType":"series"},
             "seasons":[{"id":"s1","number":1,"episodes":[{"id":"e1","seasonNumber":1,"episodeNumber":1}]}]}
            """,
        )
        assertTrue(details.availableOrderings.isEmpty())
        assertNull(details.seasons.first().episodes.first().numbering)
    }

    @Test fun prequeueKeySeparatesOrderings() {
        val series = DetailsTarget("tvdb:series:74852", "Avatar", isMovie = false, tvdbId = "74852")
        fun request(ordering: String) = PlaybackRequest(
            series, PlaybackRequest.EpisodeInfo(1, 2, numbering = EpisodeNumbering("tvdb:series:74852", ordering)),
        )
        assertNotEquals(request("official").prequeueKey, request("dvd").prequeueKey)
        assertEquals(request("dvd").prequeueKey, request("dvd").prequeueKey)
    }
}
