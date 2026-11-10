package com.strmr.mobile.ui.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.strmr.mobile.ui.components.ArtImage
import com.strmr.mobile.ui.components.CardBadgeView
import com.strmr.tv.api.Artwork
import com.strmr.tv.api.Rating
import com.strmr.tv.api.ServerArtwork
import com.strmr.tv.ui.details.DetailsModel
import com.strmr.tv.ui.details.RatingStyle

/**
 * The page's top: the backdrop running under the status bar and fading into the page, the
 * title's logo (else its name) at its foot. Taller than wide on a phone held upright, shorter
 * in landscape so the actions show.
 */
@Composable
fun DetailsHero(model: DetailsModel) {
    val target = model.target
    val config = LocalConfiguration.current
    val landscape = config.screenWidthDp > config.screenHeightDp
    val compact = config.smallestScreenWidthDp < 600
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = if (landscape) maxOf(180.dp, config.screenHeightDp.dp * 0.48f)
        else minOf(maxWidth * 1.05f, config.screenHeightDp.dp * 0.58f)
        val background = MaterialTheme.colorScheme.background
        Box(Modifier.fillMaxWidth().height(height)) {
            ArtImage(model.title?.backdrop?.url ?: target.backdropUrl, "w1280", null, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f), 0.25f to Color.Transparent, 0.5f to Color.Transparent, 1f to background,
                    ),
                ),
            )
            Box(
                Modifier.align(if (compact) Alignment.BottomCenter else Alignment.BottomStart).padding(horizontal = 16.dp)
                    .semantics { heading() },
            ) {
                val logo = Artwork.logo(model.title?.logo?.url ?: target.logoUrl)
                val name = @Composable {
                    Text(
                        model.title?.name ?: target.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                        color = Color.White, textAlign = if (compact) TextAlign.Center else TextAlign.Start, maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (logo != null) {
                    SubcomposeAsyncImage(
                        logo, target.name, contentScale = ContentScale.Fit,
                        alignment = if (compact) Alignment.BottomCenter else Alignment.BottomStart,
                        error = { name() },
                        modifier = Modifier.size(if (compact) 260.dp else 340.dp, if (compact) 90.dp else 120.dp),
                    )
                } else {
                    name()
                }
            }
        }
    }
}

/** Under the hero: the release badge and metadata line, the ratings, and the overview (tap for all of it). */
@Composable
fun DetailsInfo(model: DetailsModel, error: String?) {
    val compact = LocalConfiguration.current.smallestScreenWidthDp < 600
    val align = if (compact) Alignment.CenterHorizontally else Alignment.Start
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            model.headerBadge?.let { CardBadgeView(it, wide = true) }
            Text(
                model.metadataLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (compact) TextAlign.Center else TextAlign.Start,
            )
        }
        model.title?.ratings?.takeIf { it.isNotEmpty() }?.let { RatingsRow(it) }
        (model.title?.overview ?: model.target.overview)?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                overview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClickLabel = if (expanded) "Show less" else "Show more") {
                    expanded = !expanded
                },
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** The ratings line: one icon and score per source, in the Expo order; scrolls when it doesn't fit. */
@Composable
private fun RatingsRow(ratings: List<Rating>) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                        ServerArtwork.url("/api/static/rating_icons/${style.icon}"), null, contentScale = ContentScale.Fit,
                        error = { star() }, modifier = Modifier.size(18.dp),
                    )
                } else {
                    star()
                }
                Text(style.text, style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
    }
}

