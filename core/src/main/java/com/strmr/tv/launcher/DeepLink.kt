package com.strmr.tv.launcher

import com.strmr.tv.api.DetailsTarget
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A launcher link, the Android form of the tvOS Top Shelf's: `com.strmr.tv://details?…`
 * opens a title's details page, `com.strmr.tv://play?…` opens it and starts its primary
 * action (Resume / Play). `profile` names the profile whose rows the link came from. The
 * query carries enough of the title for details to draw before its bundle loads. Pure Kotlin
 * (no `android.net.Uri`) so it is unit tested on the JVM.
 */
data class DeepLink(val action: Action, val target: DetailsTarget, val profileId: String? = null) {
    enum class Action(val host: String) { DETAILS("details"), PLAY("play") }

    val url: String
        get() {
            val t = target
            val fields = listOf(
                "titleId" to t.titleId, "name" to t.name, "mediaType" to if (t.isMovie) "movie" else "series",
                "year" to t.year?.toString(), "tmdb" to t.tmdbId, "imdb" to t.imdbId, "tvdb" to t.tvdbId,
                "poster" to t.posterUrl, "textPoster" to t.textPosterUrl, "backdrop" to t.backdropUrl, "logo" to t.logoUrl,
                "profile" to profileId,
            )
            val query = fields.mapNotNull { (k, v) -> v?.takeIf { it.isNotEmpty() }?.let { "$k=${encode(it)}" } }
            return "$SCHEME://${action.host}?${query.joinToString("&")}"
        }

    companion object {
        const val SCHEME = "com.strmr.tv"
        /** A channel's own link (its logo / name): just opens the app. */
        const val HOME_URL = "$SCHEME://home"

        /** Null for anything that isn't a details / play link with a title ID and name. */
        fun parse(url: String?): DeepLink? {
            if (url == null || !url.startsWith("$SCHEME://")) return null
            val rest = url.removePrefix("$SCHEME://")
            val host = rest.substringBefore('?').substringBefore('/').lowercase()
            val action = Action.entries.firstOrNull { it.host == host } ?: return null
            val q = rest.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.mapNotNull { pair ->
                val key = decode(pair.substringBefore('='))
                val value = decode(pair.substringAfter('=', ""))
                if (value.isEmpty()) null else key to value
            }.toMap()
            val id = q["titleId"] ?: return null
            val name = q["name"] ?: return null
            val target = DetailsTarget(
                titleId = id, name = name, isMovie = q["mediaType"] == "movie", year = q["year"]?.toIntOrNull(),
                tmdbId = q["tmdb"], imdbId = q["imdb"], tvdbId = q["tvdb"], posterUrl = q["poster"],
                textPosterUrl = q["textPoster"], backdropUrl = q["backdrop"], logoUrl = q["logo"],
            )
            return DeepLink(action, target, q["profile"])
        }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        private fun decode(value: String) = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}
