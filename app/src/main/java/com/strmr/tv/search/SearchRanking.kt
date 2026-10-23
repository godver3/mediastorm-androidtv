package com.strmr.tv.search

import com.strmr.tv.api.Title
import java.text.Normalizer
import kotlinx.serialization.Serializable

/** One `/api/search` hit: the title and its relevance. */
@Serializable
data class SearchResult(val title: Title, val score: Double? = null)

/**
 * The pure parts of the Expo TV search (`hooks/useApi.ts` `useSearchTitles`,
 * `components/search/person-results.ts`, `TVSearchScreen.tsx`), as the tvOS port has them.
 */
object SearchRanking {
    const val MIN_QUERY_LENGTH = 2
    const val MAX_QUERY_LENGTH = 40
    const val DEBOUNCE_MS = 400L
    /** The three searches, in the order Expo sends them; each is a `type=` value. */
    val types = listOf("movie", "series", "person")

    /** NFKC, trimmed, inner whitespace collapsed, lower-cased. */
    fun normalizedName(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFKC).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString(" ").lowercase()

    /** A person whose name is the query, which Expo puts ahead of every other result. */
    fun isExactPersonMatch(title: Title, query: String) =
        title.mediaType == "person" && normalizedName(title.name) == normalizedName(query)

    /**
     * `mergeAndSortResults`: one result per title ID (the higher score wins), exact person
     * matches first, then by score; ties keep the order the results arrived in.
     */
    fun merge(results: List<SearchResult>, query: String): List<SearchResult> {
        val best = LinkedHashMap<String, SearchResult>()
        for (result in results) {
            val t = result.title
            val key = t.id.ifEmpty { "${t.mediaType}-${t.name.lowercase()}-${t.year?.toString() ?: "unknown"}" }
            val existing = best[key]
            if (existing == null || (existing.score ?: 0.0) < (result.score ?: 0.0)) best[key] = result
        }
        // sortedWith is stable, so equal scores keep their arrival order.
        return best.values.sortedWith(
            compareByDescending<SearchResult> { isExactPersonMatch(it.title, query) }.thenByDescending { it.score ?: 0.0 },
        )
    }

    /** The rail while a query is typed: the query, then result names, unique, at most 8. */
    fun suggestions(query: String, titles: List<Title>): List<String> =
        (listOf(query) + titles.map { it.name }).filter { it.isNotEmpty() }.distinct().take(8)

    /** The notice for searches that failed, by `type=`; null when none did. */
    fun failureNotice(failed: Set<String>): String? {
        if (failed.isEmpty()) return null
        if (failed.size == types.size) return "Search couldn’t complete. Please try again."
        val names = listOf("movie" to "Movie", "series" to "TV show", "person" to "Actor")
            .filter { it.first in failed }.joinToString(" and ") { it.second }
        return "$names search is unavailable. Results may be incomplete."
    }

    /** The query as Expo keeps it: at most 40 characters. */
    fun clamp(query: String) = query.take(MAX_QUERY_LENGTH)
}
