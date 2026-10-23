package com.strmr.tv.search

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Title
import com.strmr.tv.app.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * The Search page's state, a port of the Expo `TVSearchScreen` and `useSearchTitles` (as the
 * tvOS `SearchStore`): Top 10 Today before anything is typed, then the movie, series and
 * person searches (400 ms after the last keystroke, 2+ characters) merged into one grid.
 * The screen's effects drive it, so a new query cancels the last one's requests.
 */
@Stable
class SearchState {
    /** Top 10 Today, loaded once per visit. */
    var topTen by mutableStateOf<List<Title>>(emptyList()); private set
    var topTenLoading by mutableStateOf(false); private set
    private var topTenLoaded = false

    /** The merged results of `resultQuery`, and whether its searches are still running. */
    var results by mutableStateOf<List<SearchResult>>(emptyList()); private set
    var resultQuery by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    /** Total or partial failure of the current query's searches. */
    var notice by mutableStateOf<String?>(null); private set
    /** Bumped by Retry, which re-runs the query at once. */
    var retryToken by mutableIntStateOf(0); private set
    private var skipDebounce = false

    /** What the grid shows for `query` (trimmed): its results, or Top 10 Today. */
    fun titles(query: String): List<Title> = when {
        query.isEmpty() -> topTen
        resultQuery == query -> results.map { it.title }
        else -> emptyList()
    }

    fun retry() {
        skipDebounce = true
        retryToken++
    }

    /** Loads Top 10 Today into the grid, once per visit; a failure leaves it empty. */
    suspend fun loadTopTen() {
        if (topTenLoaded) return
        topTenLoading = true
        try {
            topTen = ApiClient.searchTopTen()
            topTenLoaded = true
            AppLog.i(TAG, "top ten: ${topTen.size} titles")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "top ten failed", e)
        } finally {
            topTenLoading = false
        }
    }

    /** Runs the three searches for `query` (trimmed) after the debounce; cancelled by the next query. */
    suspend fun search(query: String) {
        val immediate = skipDebounce
        skipDebounce = false
        // The previous query's results and errors go as soon as the text changes.
        results = emptyList()
        resultQuery = null
        notice = null
        if (query.length < SearchRanking.MIN_QUERY_LENGTH) {
            loading = false
            return
        }
        loading = true
        if (!immediate) delay(SearchRanking.DEBOUNCE_MS)
        resultQuery = query
        val started = System.currentTimeMillis()
        val outcomes = coroutineScope {
            SearchRanking.types.map { type ->
                type to async {
                    try {
                        Result.success(ApiClient.search(query, type))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }.map { (type, deferred) -> type to deferred.await() }
        }
        val failed = mutableSetOf<String>()
        val collected = outcomes.flatMap { (type, outcome) ->
            outcome.exceptionOrNull()?.let {
                failed += type
                AppLog.e(TAG, "$type search failed", it)
            }
            outcome.getOrNull().orEmpty()
        }
        results = SearchRanking.merge(collected, query)
        notice = SearchRanking.failureNotice(failed)
        loading = false
        val people = results.count { it.title.mediaType == "person" }
        AppLog.i(
            TAG,
            "\"$query\": ${results.size} results ($people people) in ${System.currentTimeMillis() - started} ms" +
                if (failed.isEmpty()) "" else ", failed: ${failed.sorted().joinToString(",")}",
        )
    }

    companion object {
        /** `adb logcat -s strmr.Search`. */
        const val TAG = "Search"
    }
}
