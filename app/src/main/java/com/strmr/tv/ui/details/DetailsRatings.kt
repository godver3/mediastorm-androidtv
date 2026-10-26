package com.strmr.tv.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.api.Rating
import com.strmr.tv.api.ServerArtwork
import kotlin.math.roundToInt

/**
 * How a rating source is drawn: its icon (served by the server under `/api/static/rating_icons`),
 * its colour and the score text. Mirrors the Expo details page.
 */
data class RatingStyle(val label: String, val color: Long, val icon: String?, val text: String) {
    companion object {
        /** Expo's order: IMDb, TMDB, Trakt, RT critics, RT audience, Metacritic, Letterboxd. */
        private val order = listOf("imdb", "tmdb", "trakt", "tomatoes", "audience", "metacritic", "letterboxd")

        /** Stable, so unknown sources keep the server's order after the known ones. */
        fun sorted(ratings: List<Rating>): List<Rating> =
            ratings.sortedBy { order.indexOf(it.source).takeIf { i -> i >= 0 } ?: order.size }

        fun of(rating: Rating): RatingStyle {
            val text = format(rating)
            return when (rating.source) {
                "imdb" -> RatingStyle("IMDb", 0xFFF5C518, "imdb.png", text)
                "tmdb" -> RatingStyle("TMDB", 0xFF01D277, "tmdb.png", text)
                "trakt" -> RatingStyle("Trakt", 0xFFED1C24, "trakt.png", text)
                "letterboxd" -> RatingStyle("Letterboxd", 0xFF00E054, "letterboxd.png", text)
                "tomatoes" -> {
                    // Fresh at 60% and up, on either scale.
                    val percent = if (rating.max == 100.0) rating.value else rating.value * 10
                    if (percent >= 60) RatingStyle("Rotten Tomatoes", 0xFFFA320A, "rt_critics.png", text)
                    else RatingStyle("Rotten Tomatoes", 0xFF6B8E23, "rt_rotten.png", text)
                }
                "audience" -> RatingStyle("RT Audience", 0xFFFA320A, "rt_audience.png", text)
                "metacritic" -> RatingStyle("Metacritic", 0xFFFFCC34, "metacritic.png", text)
                else -> RatingStyle(rating.source, 0xFF888888, null, text)
            }
        }

        /** IMDb and Letterboxd as decimals ("7.5", "3.5"); the percentage sources as "87%"; anything else by its scale. */
        fun format(rating: Rating): String {
            val decimal = String.format(java.util.Locale.US, "%.1f", rating.value)
            val percent = "${rating.value.roundToInt()}%"
            return when (rating.source) {
                "imdb", "letterboxd" -> decimal
                "tmdb", "trakt", "tomatoes", "audience", "metacritic" -> percent
                else -> if (rating.max == 10.0) decimal else percent
            }
        }
    }
}

/** The details header's ratings line: one icon and score per source. */
@Composable
fun RatingsRow(ratings: List<Rating>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RatingStyle.sorted(ratings).distinctBy { it.source }.forEach { rating ->
            val style = RatingStyle.of(rating)
            val color = Color(style.color)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "${style.label} ${style.text}" },
            ) {
                val star = @Composable { Icon(Icons.Filled.Star, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) }
                if (style.icon != null) {
                    SubcomposeAsyncImage(
                        model = ServerArtwork.url("/api/static/rating_icons/${style.icon}"),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        error = { star() },
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    star()
                }
                Text(style.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}
