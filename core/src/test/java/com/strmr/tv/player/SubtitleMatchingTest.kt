package com.strmr.tv.player

import com.strmr.tv.api.SubtitleSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleMatchingTest {
    private fun result(id: String, release: String, downloads: Int = 0) =
        SubtitleSearchResult(id = id, provider = "opensubtitles", release = release, downloads = downloads)

    @Test fun similarityCountsSharedTokensAndImportantBonus() {
        val media = "Movie.Name.2020.1080p.BluRay.x264-GROUP.mkv"
        // 5 of 7 tokens shared (movie name 2020 1080p x264), two of them important (+2 each).
        assertEquals(5.0 / 7 * 100 + 4, SubtitleMatching.releaseSimilarity(media, "Movie.Name.2020.1080p.WEBRip.x264-OTHER"), 1e-9)
        assertEquals(100.0 + 6, SubtitleMatching.releaseSimilarity(media, "movie name 2020 1080p bluray x264 group"), 1e-9)
    }

    @Test fun similarityIgnoresSingleCharactersExtensionsAndEmpty() {
        assertEquals(0.0, SubtitleMatching.releaseSimilarity("", "Movie"), 0.0)
        assertEquals(0.0, SubtitleMatching.releaseSimilarity("Movie", null), 0.0)
        assertEquals(0.0, SubtitleMatching.releaseSimilarity("a.b.c", "a.b.c"), 0.0)
        // "srt" is stripped as an extension, so "movie" alone matches.
        assertEquals(100.0, SubtitleMatching.releaseSimilarity("A.Movie", "Movie.srt"), 1e-9)
    }

    @Test fun episodeScore() {
        assertEquals(1000, SubtitleMatching.episodeMatchScore("Show.S01E02.720p", 1, 2))
        assertEquals(-1000, SubtitleMatching.episodeMatchScore("Show.S01E03.720p", 1, 2))
        assertEquals(1000, SubtitleMatching.episodeMatchScore("Show 1x02 HDTV", 1, 2))
        assertEquals(100, SubtitleMatching.episodeMatchScore("Show.S01.Complete", 1, 2))
        assertEquals(-1000, SubtitleMatching.episodeMatchScore("Show Season 2 pack", 1, 2))
        assertEquals(500, SubtitleMatching.episodeMatchScore("Show Episode 2", 1, 2))
        assertEquals(-500, SubtitleMatching.episodeMatchScore("Show.E05", 1, 2))
        assertEquals(0, SubtitleMatching.episodeMatchScore("Show.720p", 1, 2))
        // Movies and episode 0 score nothing.
        assertEquals(0, SubtitleMatching.episodeMatchScore("Show.S01E02", null, null))
        assertEquals(0, SubtitleMatching.episodeMatchScore("Show.S01E02", 1, 0))
    }

    @Test fun bestPrefersEpisodeThenSimilarityThenDownloads() {
        val results = listOf(
            result("wrong-episode", "Show.S01E03.1080p.WEB.x264-GRP", downloads = 9000),
            result("close", "Show.S01E02.1080p.WEB.x264-GRP", downloads = 10),
            result("far", "Show.S01E02.720p.HDTV", downloads = 500),
        )
        assertEquals("close", SubtitleMatching.selectBest(results, "Show.S01E02.1080p.WEB.x264-GRP.mkv", 1, 2).id)
        // Equal similarity: the most downloads.
        val tied = listOf(result("a", "Movie.2020", 5), result("b", "Movie.2020", 50))
        assertEquals("b", SubtitleMatching.selectBest(tied, "Movie.2020.1080p", null, null).id)
    }

    @Test fun bestWithoutReleaseNameUsesEpisodeThenDownloads() {
        val results = listOf(
            result("popular-wrong", "Show.S01E03", downloads = 9000),
            result("right", "Show.S01E02", downloads = 10),
            result("right-popular", "Show.S01E02", downloads = 20),
        )
        assertEquals("right-popular", SubtitleMatching.selectBest(results, null, 1, 2).id)
        // Ties keep the earlier result.
        assertEquals("x", SubtitleMatching.selectBest(listOf(result("x", "A", 1), result("y", "B", 1)), "", null, null).id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun bestOfNothingFails() {
        SubtitleMatching.selectBest(emptyList(), "x", null, null)
    }

    @Test fun displayOrderIsSimilarityThenDownloads() {
        val results = listOf(result("far", "Other.Thing", 999), result("near", "Movie.2020.1080p", 1), result("near2", "Movie.2020.1080p", 2))
        assertEquals(listOf("near2", "near", "far"), SubtitleMatching.sortForDisplay(results, "Movie.2020.1080p.mkv").map { it.id })
        assertEquals(results, SubtitleMatching.sortForDisplay(results, null))
    }

    @Test fun languages() {
        assertEquals("fr", SubtitleLanguages.searchCode("fre"))
        assertEquals("en", SubtitleLanguages.searchCode("eng"))
        assertEquals("xx", SubtitleLanguages.searchCode("xx"))
        assertEquals(listOf("en", "ja"), SubtitleLanguages.allowed(listOf("eng", "jpn")).map { it.code })
        assertEquals("nor", SubtitleLanguages.canonical("nob"))
        assertEquals("deu", SubtitleLanguages.canonical("de-DE"))
    }
}
