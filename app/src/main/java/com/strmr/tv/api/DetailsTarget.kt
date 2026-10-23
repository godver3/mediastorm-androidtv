package com.strmr.tv.api

/**
 * What a details page shows; built from a Continue Watching row or a list title, so the
 * page can render at once before metadata loads.
 */
data class DetailsTarget(
    val titleId: String,
    val name: String,
    val isMovie: Boolean,
    val year: Int? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
    val posterUrl: String? = null,
    /** The poster with its title text, when the server's `posterUrl` is textless. */
    val textPosterUrl: String? = null,
    val backdropUrl: String? = null,
    /** Known up front from Continue Watching and lists, so details can show it at once. */
    val logoUrl: String? = null,
    val overview: String? = null,
) {
    /** Portrait cards show the poster with its title text. */
    val displayPosterUrl: String? get() = textPosterUrl ?: posterUrl

    companion object {
        fun from(item: ContinueWatchingItem) = DetailsTarget(
            titleId = item.seriesId,
            name = item.seriesTitle,
            isMovie = item.isMovie,
            year = item.year,
            tmdbId = item.externalIds?.get("tmdb"),
            imdbId = item.externalIds?.get("imdb"),
            tvdbId = item.externalIds?.get("tvdb"),
            posterUrl = item.posterUrl,
            textPosterUrl = item.textPosterUrl,
            backdropUrl = item.backdropUrl,
            logoUrl = item.logoUrl,
            overview = item.overview,
        )

        fun from(title: Title) = DetailsTarget(
            titleId = title.id,
            name = title.name,
            isMovie = title.isMovie,
            year = title.year,
            tmdbId = title.tmdbId?.toString(),
            imdbId = title.imdbId,
            tvdbId = title.tvdbId?.toString(),
            posterUrl = title.poster?.url,
            textPosterUrl = title.textPoster?.url,
            backdropUrl = title.backdrop?.url,
            logoUrl = title.logo?.url,
            overview = title.overview,
        )
    }
}

object Artwork {
    /** TMDB "original" images are multi-megabyte; a sized rendition is plenty on a TV. */
    fun url(raw: String?, size: String = "w1280"): String? = raw?.replace("/t/p/original/", "/t/p/$size/")

    /**
     * A title logo. TMDB serves some logos as SVG, which the image loader can't draw; the
     * same path with `.png` comes back rasterized.
     */
    fun logo(raw: String?, size: String = "w500"): String? {
        var value = raw ?: return null
        if (value.endsWith(".svg", ignoreCase = true) && value.contains("image.tmdb.org")) value = value.dropLast(4) + ".png"
        return url(value, size)
    }
}
