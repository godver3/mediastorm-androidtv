package com.strmr.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.ArtImage
import com.strmr.mobile.ui.components.CardShape
import com.strmr.mobile.ui.components.LandscapeCard
import com.strmr.mobile.ui.components.PosterCard
import com.strmr.tv.home.ExploreCard
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeShelf

/**
 * One titled row, scrolling sideways. A row longer than its cap has the explore card at its
 * start or end (the profile's `exploreCardPosition`), and its title opens the whole list too.
 */
@Composable
fun ShelfRow(
    shelf: HomeShelf,
    landscape: Boolean,
    showTitles: Boolean,
    onCard: (HomeCard) -> Unit,
    onMenu: (HomeCard) -> Unit,
    onExplore: (ExploreCard) -> Unit,
) {
    val width = if (landscape) HomeMetrics.landscapeWidth() else HomeMetrics.posterWidth()
    val explore = shelf.explore
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfHeader(shelf.title, onOpen = explore?.let { { onExplore(it) } })
        LazyRow(
            contentPadding = PaddingValues(horizontal = HomeMetrics.gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (explore != null && shelf.exploreFirst) item(key = ExploreCard.ID) { ExploreTile(explore, landscape, width) { onExplore(explore) } }
            items(shelf.shown, key = { it.id }) { card ->
                if (landscape) {
                    LandscapeCard(card, width, onClick = { onCard(card) }, onLongClick = { onMenu(card) }, showTitle = showTitles)
                } else {
                    // Posters carry no text on a phone's rows (as the iPhone app); the art names them.
                    PosterCard(card, width, onClick = { onCard(card) }, onLongClick = { onMenu(card) }, showTitle = false)
                }
            }
            if (explore != null && !shelf.exploreFirst) item(key = ExploreCard.ID) { ExploreTile(explore, landscape, width) { onExplore(explore) } }
        }
    }
}

/** A row's title; with [onOpen] it is a link to the whole list, with a chevron. */
@Composable
fun ShelfHeader(title: String, onOpen: (() -> Unit)?) {
    Row(
        Modifier.padding(horizontal = HomeMetrics.gutter)
            .then(if (onOpen != null) Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onOpen) else Modifier)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (onOpen != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "See all", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The card closing (or opening) a row that holds more than its cap: four of the titles it
 * doesn't show and how many more there are. It opens the whole list.
 */
@Composable
private fun ExploreTile(explore: ExploreCard, landscape: Boolean, width: Dp, onClick: () -> Unit) {
    val urls = if (landscape) explore.artworkUrls else explore.posterUrls
    Box(
        Modifier.width(width).aspectRatio(if (landscape) 16f / 9f else 2f / 3f).clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "See all in ${explore.title}" },
    ) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until 2) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (column in 0 until 2) {
                        ArtImage(urls.getOrNull(row * 2 + column), "w342", null, Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+${explore.moreCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Text("See All", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

/** A row still loading: its title over grey posters. */
@Composable
fun PlaceholderRow(title: String) {
    val width = HomeMetrics.posterWidth()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfHeader(title, onOpen = null)
        LazyRow(
            contentPadding = PaddingValues(horizontal = HomeMetrics.gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            items(6) {
                Box(Modifier.size(width, width * 1.5f).clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            }
        }
    }
}
