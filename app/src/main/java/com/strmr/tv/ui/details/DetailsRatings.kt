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
