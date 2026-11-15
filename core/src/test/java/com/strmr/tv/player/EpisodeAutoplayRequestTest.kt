package com.strmr.tv.player

import com.strmr.tv.api.DetailsTarget
import org.junit.Assert.*
import org.junit.Test

class EpisodeAutoplayRequestTest {
    @Test fun `up next crosses seasons using the supplied aired order and starts at zero`() {
        val next = PlaybackRequest.EpisodeInfo(2,1,name="Synthetic next",itemId="synthetic-s2e1",imageUrl="/synthetic.jpg")
        val later = PlaybackRequest.EpisodeInfo(2,2)
        val request = PlaybackRequest(DetailsTarget("tmdb:series:7","Synthetic series",false),episode=PlaybackRequest.EpisodeInfo(1,10),following=listOf(next,later))
        val target = request.nextEpisode!!
        assertEquals(next,target.episode); assertNull(target.start); assertEquals(listOf(later),target.following)
        assertEquals("synthetic-s2e1",target.progressItemId)
    }
    @Test fun `repeated preparation never consumes or skips the first next episode`() {
        val first = PlaybackRequest.EpisodeInfo(1,2)
        val request = PlaybackRequest(DetailsTarget("tmdb:series:7","Synthetic series",false),episode=PlaybackRequest.EpisodeInfo(1,1),following=listOf(first,PlaybackRequest.EpisodeInfo(1,3)))
        repeat(3) { assertEquals(first,request.nextEpisode!!.episode) }
        assertNull(request.copy(following=emptyList()).nextEpisode)
    }
}
