package com.strmr.mobile.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.App

/** The profile's recent searches on this device, newest first (Expo keeps eight per profile). */
class RecentSearches(private val profileId: String = ApiClient.profileId) {
    private val prefs by lazy { App.context.getSharedPreferences("strmr.mobile", Context.MODE_PRIVATE) }
    private val key get() = "recentSearches.$profileId"

    var items by mutableStateOf(read())
        private set

    fun add(query: String) {
        items = adding(items, query)
        save()
    }

    fun clear() {
        items = emptyList()
        save()
    }

    private fun read(): List<String> =
        prefs.getString(key, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private fun save() = prefs.edit().putString(key, items.joinToString(SEPARATOR)).apply()

    companion object {
        const val LIMIT = 8
        private const val SEPARATOR = "\n"

        /** [query] (trimmed) first, its earlier spelling in any case dropped, at most [LIMIT]. */
        fun adding(items: List<String>, query: String): List<String> {
            val q = query.trim().replace(SEPARATOR, " ")
            if (q.isEmpty()) return items
            return (listOf(q) + items.filterNot { it.equals(q, ignoreCase = true) }).take(LIMIT)
        }
    }
}
