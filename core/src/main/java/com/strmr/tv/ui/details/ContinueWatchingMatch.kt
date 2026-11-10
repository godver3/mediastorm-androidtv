package com.strmr.tv.ui.details

import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.DetailsTarget

/** The Continue Watching entry for this title: its ID, or a shared provider ID. */
fun ContinueWatchingItem.isTitle(target: DetailsTarget): Boolean {
    if (isMovie != target.isMovie) return false
    if (seriesId == target.titleId) return true
    val ids = externalIds ?: return false
    return listOf("tmdb" to target.tmdbId, "imdb" to target.imdbId, "tvdb" to target.tvdbId)
        .any { (key, value) -> !value.isNullOrEmpty() && ids[key] == value }
}
