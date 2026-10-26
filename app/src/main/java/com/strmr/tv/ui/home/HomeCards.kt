package com.strmr.tv.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import com.strmr.tv.ui.cardFocusBorder
import com.strmr.tv.api.Artwork
import com.strmr.tv.home.CardBadge
import com.strmr.tv.home.CardText
import com.strmr.tv.home.ExploreCard
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.ShelfStyle
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.CardFocusScale
import com.strmr.tv.ui.ContentMargin
import com.strmr.tv.ui.captionGap

/**
 * A row's card geometry, as tvOS sizes it (its points halved for the 540 dp screen): every
 * card is the same height, 135 dp at the Large size; posters are 2:3, landscape cards 16:9,
 * and an expanding poster opens to 16:9 at the same height.
 */
data class CardMetrics(val style: ShelfStyle, val scale: Double, val text: CardText) {
    /** The title and line go under the card. */
    val captions: Boolean get() = text == CardText.BELOW
    val height: Dp get() = Math.round(BASE_HEIGHT * scale).toFloat().dp
    private val posterWidth: Dp get() = height * (2f / 3f)
    private val landscapeWidth: Dp get() = height * (16f / 9f)
    val width: Dp get() = if (style == ShelfStyle.LANDSCAPE) landscapeWidth else posterWidth
    /** The focused card's width: wider only for expanding posters. */
    val focusedWidth: Dp get() = if (style == ShelfStyle.EXPANDING_POSTER) landscapeWidth else width
    val showsArtwork16x9: Boolean get() = style == ShelfStyle.LANDSCAPE
    /** Room above the cards for the focus scale, 15 dp at the Large size. */
    val liftRoom: Dp get() = Math.round(15 * height.value / BASE_HEIGHT).toFloat().dp
    /** Card to caption, clear of the focus scale. */
    val captionGap: Dp get() = captionGap(height)
    /**
     * A whole row: its title, the lift room, the card and its caption (tvOS `rowHeight`). Without
     * captions the gap stays, plus 12 dp so the next row's title sits nearer its own cards than
     * a focused card above it.
     */
    val rowHeight: Dp get() = 23.dp + liftRoom + height + captionGap + (if (captions) 33.dp else 12.dp) + 10.dp

    companion object {
        const val BASE_HEIGHT = 135f
    }
}

/**
 * A Home card: Compose for TV's Card, which scales and outlines on focus. Posters show the
 * title's poster; landscape cards (and an expanding poster while focused) its 16:9 art.
 */
@Composable
fun HomeCardView(
    card: HomeCard,
    metrics: CardMetrics,
    showTitle: Boolean,
    /** How far the card has opened to 16:9, 0 to 1, read as it animates (the row's [RowFocus]). */
    open: () -> Float,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(Modifier.openWidth(metrics, open), verticalArrangement = Arrangement.spacedBy(metrics.captionGap)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(),
            modifier = modifier.fillMaxWidth().height(metrics.height).onFocusChanged {
                if (it.isFocused) onFocus()
            },
        ) {
            CardArt(card, metrics, open)
        }
        if (showTitle) CardTitle(card.title, card.subtitle)
    }
}

/**
 * The card's width as it opens: read at layout, so the animation re-lays the card out each
 * frame without recomposing it. Whole px, as the row's scroll counts them.
 */
private fun Modifier.openWidth(metrics: CardMetrics, open: () -> Float) = layout { measurable, constraints ->
    val card = metrics.width.roundToPx()
    val width = card + ((metrics.focusedWidth.roundToPx() - card) * open()).roundToInt()
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(width, placeable.height) { placeable.place(0, 0) }
}

/** Recomposes only when [test] on the open amount flips, not every frame. */
@Composable
private fun openPast(open: () -> Float, test: (Float) -> Boolean): Boolean {
    val past by remember(open) { derivedStateOf { test(open()) } }
    return past
}

/**
 * The card's art. An expanding poster draws its 16:9 art under the poster, which fades out at
 * its own width while the card widens around it (and back in as it folds), rather than
 * swapping art the moment focus moves.
 */
@Composable
private fun CardArt(card: HomeCard, metrics: CardMetrics, open: () -> Float) {
    val opening = openPast(open) { it > 0f }
    val folded = openPast(open) { it < 1f }
    val wide = metrics.showsArtwork16x9 || openPast(open) { it > 0.5f }
    Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E)), contentAlignment = Alignment.Center) {
        when {
            card.drawable != null -> Image(painterResource(card.drawable), card.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            card.logoTile -> AsyncImage(
                model = card.logoUrl, contentDescription = card.title, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(18.dp),
            )
            metrics.showsArtwork16x9 -> CardImage(card.artworkUrl ?: card.posterUrl, "w780", card.title, Modifier.fillMaxSize())
            else -> {
                if (opening) CardImage(card.artworkUrl ?: card.posterUrl, "w780", null, Modifier.fillMaxSize())
                if (folded) {
                    CardImage(
                        card.posterUrl ?: card.artworkUrl, "w342", card.title,
                        Modifier.width(metrics.width).fillMaxHeight().graphicsLayer { alpha = 1f - open() },
                    )
                }
            }
        }
        if (card.drawable != null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
            Text(card.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
        // Inset as tvOS places them: closer to the edge on the narrower poster.
        val inset = if (wide) 8.dp else 5.dp
        val progress = showsProgress(card.progress)
        if (metrics.text.overlays(wide) && card.drawable == null && !card.logoTile) {
            CardOverlay(card.title, card.subtitle, inset, progress, Modifier.align(Alignment.BottomStart))
        }
        // A poster's "New" sits above its other badge; a 16:9 card's at the top right (as Expo).
        val new = card.newEpisode
        if (!wide && new != null) {
            Column(Modifier.align(Alignment.TopStart).padding(inset), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NewEpisodeBadgeView(new)
                Badges(card)
            }
        } else {
            Badges(card, Modifier.align(Alignment.TopStart).padding(inset), wide = wide)
            if (new != null) NewEpisodeBadgeView(new, Modifier.align(Alignment.TopEnd).padding(inset))
        }
        if (progress) ProgressLine(card.progress, Modifier.align(Alignment.BottomStart).padding(inset))
    }
}

@Composable
private fun CardImage(url: String?, size: String, description: String?, modifier: Modifier) {
    AsyncImage(model = Artwork.url(url, size), contentDescription = description, contentScale = ContentScale.Crop, modifier = modifier)
}

/**
 * A badge on artwork (tvOS ArtworkBadge). On a poster a status shows as its symbol in a small
 * glass disc; a 16:9 card ([wide]: landscape, or a poster opened on focus) has room for the text capsule.
 */
@Composable
fun Badge(badge: CardBadge, modifier: Modifier = Modifier, wide: Boolean = false) {
    val icon = badge.symbol?.icon
    if (icon == null || wide) {
        BadgeLabel(badge.text, badge.symbol, modifier = modifier)
        return
    }
    Box(
        modifier.size(26.dp)
            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = badge.text, tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

/** A card's badges stacked in its top-left corner, as the Expo app stacks a release status with the watch state. */
@Composable
fun Badges(card: HomeCard, modifier: Modifier = Modifier, wide: Boolean = false) {
    val badges = listOfNotNull(card.badge, card.watchBadge)
    if (badges.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { badges.forEach { Badge(it, wide = wide) } }
}

/** Small capsule label on artwork or beside metadata, with an optional leading symbol. */
@Composable
fun BadgeLabel(text: String, symbol: CardBadge.Symbol? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbol?.icon?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp)) }
        Text(text.uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.25.sp)
    }
}

private val CardBadge.Symbol.icon: ImageVector
    get() = when (this) {
        CardBadge.Symbol.COMING_SOON -> AppIcons.Event
        CardBadge.Symbol.UNRELEASED -> AppIcons.Hourglass
        CardBadge.Symbol.IN_THEATERS -> AppIcons.Theaters
        CardBadge.Symbol.WATCHED -> Icons.Filled.Check
        CardBadge.Symbol.IN_PROGRESS -> AppIcons.WatchedPartial
    }

/** Whether a card shows its resume progress: only between 5% and 90% watched. */
fun showsProgress(progress: Float): Boolean = progress > 0.05f && progress < 0.9f

/** Resume progress: a white capsule on a faint track, inset in the card (the tvOS bar). */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color.White, RoundedCornerShape(50)))
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 15.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, fontSize = 12.5.sp, lineHeight = 15.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The card text over its bottom edge (Expo's landscape text overlay): the title, then the
 * year / episode line, on a gradient to near black, clear of the progress line.
 */
@Composable
private fun CardOverlay(title: String, subtitle: String, inset: Dp, progress: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.7f), 1f to Color.Black.copy(alpha = 0.95f)))
            .padding(start = inset, end = inset, top = 16.dp, bottom = inset + if (progress) 6.dp else 0.dp),
    ) {
        Text(title, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, fontSize = 11.sp, lineHeight = 13.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The explore card: a 2×2 collage of titles the row doesn't show and "+N"; opens the whole list. */
@Composable
fun ExploreCardView(
    explore: ExploreCard,
    metrics: CardMetrics,
    showTitle: Boolean,
    open: () -> Float,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wide = metrics.showsArtwork16x9 || openPast(open) { it > 0.5f }
    val images = if (wide) explore.artworkUrls else explore.posterUrls
    Column(Modifier.openWidth(metrics, open), verticalArrangement = Arrangement.spacedBy(metrics.captionGap)) {
        Card(
            onClick = onClick,
            scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(),
            modifier = modifier.fillMaxWidth().height(metrics.height).onFocusChanged {
                if (it.isFocused) onFocus()
            },
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
                Column(Modifier.fillMaxSize()) {
                    for (r in 0..1) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            for (c in 0..1) {
                                AsyncImage(
                                    model = Artwork.url(images.getOrNull(r * 2 + c), "w342"), contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Text("+${explore.moreCount}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                if (metrics.text.overlays(wide)) CardOverlay("See All", explore.title, if (wide) 8.dp else 5.dp, false, Modifier.align(Alignment.BottomStart))
            }
        }
        if (showTitle) CardTitle("See All", explore.title)
    }
}

/** A row still loading (or that failed): its title over empty card shapes, so nothing moves when it arrives. */
@Composable
fun PlaceholderRow(title: String, failed: Boolean, metrics: CardMetrics) {
    Column(Modifier.height(metrics.rowHeight)) {
        Text(if (failed) "$title — couldn't load" else title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
             color = Color.White.copy(alpha = if (failed) 0.5f else 1f), modifier = Modifier.padding(start = ContentMargin))
        Row(Modifier.padding(start = ContentMargin, top = metrics.liftRoom), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            repeat(6) {
                Box(Modifier.size(metrics.width, metrics.height).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp)))
            }
        }
    }
}
