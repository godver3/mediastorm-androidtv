package com.strmr.mobile.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.strmr.mobile.ui.components.ArtImage
import com.strmr.mobile.ui.components.CardShape
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.tv.api.Artwork
import com.strmr.tv.home.BrowseList
import com.strmr.tv.ui.lists.BrowsePreviews
import com.strmr.tv.ui.lists.TileArt
import com.strmr.tv.ui.lists.TileSpec

private val TILE_WIDTH = 200.dp
private val TILE_HEIGHT = 112.dp

/** A titled row of list tiles; [onNewList] ends it with a New List tile. Nothing shows for no tiles. */
@Composable
fun TileRow(title: String, tiles: List<TileSpec>, onNewList: (() -> Unit)? = null) {
    if (tiles.isEmpty() && onNewList == null) return
    val navigator = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(tiles, key = { _, t -> t.list.toString() + (t.title ?: "") }) { index, tile ->
                ListTile(tile, index) { navigator.openList(tile.list) }
            }
            if (onNewList != null) item(key = "new-list") { NewListTile(onNewList) }
        }
    }
}

/**
 * A list's tile: a 2×2 poster collage for the profile's own lists of four or more, otherwise a
 * backdrop of one of the list's first titles (neighbours take different ones), the name over it.
 */
@Composable
private fun ListTile(tile: TileSpec, position: Int, onClick: () -> Unit) {
    var fetched by remember(tile.list) { mutableStateOf<TileArt?>(null) }
    // Keyed on the count so a list whose contents changed refetches its collage.
    LaunchedEffect(tile.list, tile.count) {
        if (tile.art == null && tile.logo == null) fetched = BrowsePreviews.art(tile.list, tile.count)
    }
    val preview = tile.art ?: fetched
    val personal = tile.list is BrowseList.Custom || tile.list == BrowseList.Watchlist ||
        tile.list == BrowseList.PermanentPrequeue || tile.list == BrowseList.ContinueWatching
    val name = tile.title ?: tile.list.title
    Box(
        Modifier.size(TILE_WIDTH, TILE_HEIGHT).clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        when {
            tile.logo != null -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AsyncImage(Artwork.url(tile.logo, "w500"), name, contentScale = ContentScale.Fit, modifier = Modifier.size(130.dp, 56.dp))
            }
            personal && (preview?.posters?.size ?: 0) >= 4 -> Collage(preview!!.posters)
            else -> {
                val backdrop = (tile.list as? BrowseList.Similar)?.backdrop ?: preview?.backdrops?.takeIf { it.isNotEmpty() }
                    ?.let { if (personal) it[0] else it[position % it.size] }
                if (backdrop != null) ArtImage(backdrop, "w780", null, Modifier.fillMaxSize())
            }
        }
        if (tile.logo == null) TileLabel(name, tile.count, tile.subtitle, Modifier.align(Alignment.BottomStart))
    }
}

/** The name and count (or subtitle) over a scrim; white, as it sits on artwork. */
@Composable
private fun TileLabel(name: String, count: Int?, subtitle: String?, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
            .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 7.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val line = count?.let { if (it == 1) "1 title" else "$it titles" } ?: subtitle
        line?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f)) }
    }
}

/** Posters cropped into the tile's four quarters. */
@Composable
private fun Collage(posters: List<String>) {
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (column in 0 until 2) {
                    ArtImage(posters.getOrNull(row * 2 + column), "w342", null, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun NewListTile(onClick: () -> Unit) {
    Column(
        Modifier.size(TILE_WIDTH, TILE_HEIGHT).clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("New List", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
