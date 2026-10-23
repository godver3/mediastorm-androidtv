package com.strmr.tv.launcher

import com.strmr.tv.api.DetailsTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test fun roundTripsEveryField() {
        val target = DetailsTarget(
            titleId = "tmdb:tv:1399", name = "Game of Thrones & Friends: S/E?", isMovie = false, year = 2011,
            tmdbId = "1399", imdbId = "tt0944947", tvdbId = "121361",
            posterUrl = "https://image.tmdb.org/t/p/w500/a b.jpg?x=1&y=2", textPosterUrl = "https://x/tp.jpg",
            backdropUrl = "https://x/b.jpg", logoUrl = "https://x/l.png",
        )
        val link = DeepLink(DeepLink.Action.PLAY, target, "792c4263")
        assertEquals(link, DeepLink.parse(link.url))
    }

    @Test fun omitsEmptyFieldsAndParsesMinimalLinks() {
        val link = DeepLink(DeepLink.Action.DETAILS, DetailsTarget("tmdb:movie:1", "Movie", isMovie = true))
        assertEquals("com.strmr.tv://details?titleId=tmdb%3Amovie%3A1&name=Movie&mediaType=movie", link.url)
        assertEquals(link, DeepLink.parse(link.url))
    }

    @Test fun rejectsOtherSchemesActionsAndMissingFields() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse("com.strmr.app://details?titleId=x&name=y"))
        assertNull(DeepLink.parse("com.strmr.tv://shelf?titleId=x&name=y"))
        assertNull(DeepLink.parse(DeepLink.HOME_URL))
        assertNull(DeepLink.parse("com.strmr.tv://details?name=y"))
        assertNull(DeepLink.parse("com.strmr.tv://play?titleId=x"))
    }
}
