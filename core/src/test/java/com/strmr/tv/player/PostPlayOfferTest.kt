package com.strmr.tv.player

import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.library.ProfileDataContext
import org.junit.Assert.*
import org.junit.Test

class PostPlayOfferTest {
    private val movie = PlaybackRequest(DetailsTarget("tmdb:movie:7","Synthetic",true))
    @Test fun `a real movie end is offered once`() {
        val policy = PostPlayOffer(); assertTrue(policy.offer(movie,true,false,false,90_000,90_000,false)); assertFalse(policy.offer(movie,true,false,false,90_000,90_000,false))
    }
    @Test fun `a normal episode never interrupts the next episode with a rating`() {
        val episode = movie.copy(title = movie.title.copy(isMovie = false),episode = PlaybackRequest.EpisodeInfo(1,1),episodeOrderKnown = true,following = listOf(PlaybackRequest.EpisodeInfo(1,2)))
        assertFalse(PostPlayOffer().offer(episode,true,false,false,90_000,90_000,false))
        assertTrue(PostPlayOffer().offer(episode.copy(following = emptyList()),true,false,false,90_000,90_000,false))
    }
    @Test fun `errors early ends trailers live and wrong-content reports do not offer feedback`() {
        assertFalse(PostPlayOffer().offer(movie,true,false,false,10_000,90_000,false))
        assertFalse(PostPlayOffer().offer(movie,true,false,false,90_000,90_000,true))
        assertFalse(PostPlayOffer().offer(movie,false,false,false,90_000,90_000,false))
        assertFalse(PostPlayOffer().offer(movie,true,true,false,90_000,90_000,false))
        assertFalse(PostPlayOffer().offer(movie,true,false,true,90_000,90_000,false))
        assertFalse(PostPlayOffer().offer(movie,true,false,false,0,0,false))
    }
    @Test fun `an old profile context cannot offer an end screen`() {
        val policy = PostPlayOffer(); ProfileDataContext.invalidate(); assertFalse(policy.offer(movie,true,false,false,90_000,90_000,false))
    }
    @Test fun `missing or local-only episode ordering never pretends to be an end of series`() {
        val episode = movie.copy(title = movie.title.copy(isMovie = false),episode = PlaybackRequest.EpisodeInfo(1,1))
        assertFalse(PostPlayOffer().offer(episode,true,false,false,90_000,90_000,false))
    }
}
