package com.strmr.mobile.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.home.HomeCard

val CardShape = RoundedCornerShape(10.dp)
private val Placeholder = Color(0xFF1C1C1E)

/** Whether a card shows its resume progress: started and under 90% watched. */
fun showsProgress(progress: Float): Boolean = progress > 0f && progress < 0.9f

/** Resume progress: the accent on a faint track, inset at the card's bottom. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.primary))
    }
}

/** A TMDB-sized image (`w342`, `w780`…) filling its box. */
@Composable
fun ArtImage(url: String?, size: String, description: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    AsyncImage(model = Artwork.url(url, size), contentDescription = description, contentScale = contentScale, modifier = modifier)
}

/** Tap opens, long press (with a haptic) shows the card's menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.cardClicks(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier {
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
    )
}

/** A 2:3 poster card with its title (and subtitle) under it. */
@Composable
fun PosterCard(
    card: HomeCard,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(CardShape).background(Placeholder)
                .cardClicks(onClick, onLongClick),
        ) {
            CardArt(card, wide = false)
        }
        if (showTitle) CardText(card.title, card.subtitle)
    }
}

/** A 16:9 card with its title under it (Continue Watching, landscape shelves, channels). */
@Composable
fun LandscapeCard(
    card: HomeCard,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(CardShape).background(Placeholder)
                .cardClicks(onClick, onLongClick),
        ) {
            CardArt(card, wide = true)
        }
        if (showTitle && card.drawable == null) CardText(card.title, card.subtitle)
    }
}

@Composable
private fun CardArt(card: HomeCard, wide: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            card.drawable != null -> Image(painterResource(card.drawable!!), card.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            card.logoTile && card.logoUrl == null -> Text(card.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            card.logoTile -> AsyncImage(card.logoUrl, card.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
            card.collageUrls.isNotEmpty() -> ArtCollage(card.collageUrls)
            wide -> ArtImage(card.artworkUrl ?: card.posterUrl, "w780", card.title, Modifier.fillMaxSize())
            else -> ArtImage(card.posterUrl ?: card.artworkUrl, "w342", card.title, Modifier.fillMaxSize())
        }
        if (card.drawable != null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
            Text(
                card.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        CardBadges(card, Modifier.align(Alignment.TopStart).padding(6.dp), wide = wide)
        if (showsProgress(card.progress)) ProgressLine(card.progress, Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

/** Four images in the card's quarters (a hub card's preview of its list). */
@Composable
private fun ArtCollage(urls: List<String>) {
    Column(Modifier.fillMaxSize()) {
        for (r in 0..1) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0..1) {
                    ArtImage(urls.getOrNull(r * 2 + c), "w300", null, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
fun CardText(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
