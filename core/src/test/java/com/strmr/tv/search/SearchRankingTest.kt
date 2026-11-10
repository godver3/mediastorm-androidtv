package com.strmr.tv.search

import com.strmr.tv.api.Title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Expo TV search's merge, person matching, suggestions and failure notices
 * (`hooks/useApi.ts`, `components/search/person-results.ts`, `TVSearchScreen.tsx`).
 */
class SearchRankingTest {
    private fun result(id: String, name: String, type: String, score: Double?, year: Int? = null) =
        SearchResult(Title(id = id, name = name, mediaType = type, year = year), score)

    @Test fun sortsByScoreKeepingArrivalOrderForTies() {
        val merged = SearchRanking.merge(
            listOf(result("m1", "Batman Begins", "movie", 80.0), result("m2", "The Batman", "movie", 90.0), result("s1", "Batman", "series", 80.0)),
            "batman",
        )
        assertEquals(listOf("m2", "m1", "s1"), merged.map { it.title.id })
    }

    @Test fun exactPersonMatchLeadsWhateverItsScore() {
        val merged = SearchRanking.merge(
            listOf(
                result("m1", "Tom Hanks: The Nomad", "movie", 300.0),
                result("p1", "Tom Hanks", "person", 14.0),
                result("p2", "Tom Hanks Jr", "person", 20.0),
            ),
            "  tom   HANKS ",
        )
        assertEquals(listOf("p1", "m1", "p2"), merged.map { it.title.id })
    }

    @Test fun duplicateIdsKeepTheHigherScore() {
        val merged = SearchRanking.merge(
            listOf(result("m1", "Old", "movie", 10.0), result("m1", "New", "movie", 50.0), result("m1", "Lower", "movie", 20.0)),
            "x",
        )
        assertEquals(listOf("New"), merged.map { it.title.name })
    }

    @Test fun missingIdsDedupeByTypeNameAndYear() {
        val merged = SearchRanking.merge(
            listOf(result("", "Heat", "movie", 1.0, 1995), result("", "HEAT", "movie", 2.0, 1995), result("", "Heat", "series", 1.0, 1995)),
            "heat",
        )
        assertEquals(listOf("HEAT", "Heat"), merged.map { it.title.name })
    }

    @Test fun normalizedNameIsNfkcTrimmedCollapsedAndLowercased() {
        assertEquals("tom hanks", SearchRanking.normalizedName("  Ｔｏｍ\t Hanks \n"))
    }

    @Test fun suggestionsAreTheQueryThenUniqueNamesUpToEight() {
        val titles = (1..10).map { result("$it", if (it == 2) "Batman" else "Title ${it % 4}", "movie", 1.0).title }
        assertEquals(listOf("Batman", "Title 1", "Title 3", "Title 0", "Title 2"), SearchRanking.suggestions("Batman", titles))
        val many = (1..20).map { result("$it", "T$it", "movie", 1.0).title }
        assertEquals(8, SearchRanking.suggestions("q", many).size)
    }

    @Test fun failureNotices() {
        assertNull(SearchRanking.failureNotice(emptySet()))
        assertEquals("Search couldn’t complete. Please try again.", SearchRanking.failureNotice(setOf("movie", "series", "person")))
        assertEquals("Movie and Actor search is unavailable. Results may be incomplete.", SearchRanking.failureNotice(setOf("person", "movie")))
        assertEquals("TV show search is unavailable. Results may be incomplete.", SearchRanking.failureNotice(setOf("series")))
    }

    @Test fun queryIsClampedToFortyCharacters() {
        assertEquals(40, SearchRanking.clamp("a".repeat(50)).length)
    }
}
