package com.strmr.tv.ui.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.ui.cardFocusBorder
import com.strmr.tv.ui.CardFocusScale
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Artwork
import com.strmr.tv.home.BrowseEntry
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.ShelfPhase
import com.strmr.tv.home.browsePage
import com.strmr.tv.ui.ContentMargin
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.MinimalScroll

/** A tile's size (tvOS 480 × 270 pt). */
private val TILE_WIDTH = 240.dp
private val TILE_HEIGHT = 135.dp

/** Artwork of a list's first titles. */
data class TileArt(val posters: List<String> = emptyList(), val backdrops: List<String> = emptyList())

/**
 * A tile to show: its list, its size when known up front, and artwork when the tab already
 * has it (otherwise the tile fetches the list's first titles). `title` overrides the list's
 * name (collection hub cards); `logo` draws on a plain panel instead (services, hub cards).
 */
data class TileSpec(
    val list: BrowseList,
    val title: String? = null,
    val count: Int? = null,
    val art: TileArt? = null,
    val logo: String? = null,
    /** Under the name when there is no count (a library's source and kind). */
    val subtitle: String? = null,
)

/** A titled row of list tiles; focus returns to the tile last focused in it, as on Home. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileRow(title: String, tiles: List<TileSpec>, onNewList: (() -> Unit)? = null) {
    val navigator = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = ContentMargin))
        // Scrolls only as far as the focused tile needs, so the row doesn't slide on every move.
        CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ContentMargin, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.focusRestorer(),
            ) {
                itemsIndexed(tiles, key = { _, t -> t.list.toString() + (t.title ?: "") }) { index, tile ->
                    ListTile(tile, index) { navigator.openList(tile.list) }
                }
                if (onNewList != null) item(key = "new-list") { NewListTile(onNewList) }
            }
        }
    }
}

/**
 * A list's tile: a 2×2 poster collage for the profile's own lists of four or more, otherwise
 * a backdrop from one of the list's first titles, with the list's name over it. Neighbouring
 * lists often share a top title, so each tile takes a different one of its first few (`position`).
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
    Card(onClick = onClick, scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(), modifier = Modifier.size(TILE_WIDTH, TILE_HEIGHT)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF2C2C2E))) {
            when {
                tile.logo != null -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    AsyncImage(Artwork.url(tile.logo, "w500"), name, contentScale = ContentScale.Fit, modifier = Modifier.size(150.dp, 65.dp))
                }
                personal && (preview?.posters?.size ?: 0) >= 4 -> Collage(preview!!.posters)
                else -> {
                    val backdrop = (tile.list as? BrowseList.Similar)?.backdrop ?: preview?.backdrops?.takeIf { it.isNotEmpty() }
                        ?.let { if (personal) it[0] else it[position % it.size] }
                    if (backdrop != null) AsyncImage(Artwork.url(backdrop, "w780"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            if (tile.logo == null) Label(name, tile.count, tile.subtitle, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun Label(name: String, count: Int?, subtitle: String?, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
            .padding(start = 11.dp, end = 11.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val line = count?.let { if (it == 1) "1 title" else "$it titles" } ?: subtitle
        line?.let { Text(it, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.6f)) }
    }
}

/** Posters cropped into the four quarters of the tile. */
@Composable
private fun Collage(posters: List<String>) {
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (column in 0 until 2) {
                    AsyncImage(
                        Artwork.url(posters.getOrNull(row * 2 + column), "w342"), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Creates a custom list. */
@Composable
private fun NewListTile(onClick: () -> Unit) {
    Card(onClick = onClick, scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(), modifier = Modifier.size(TILE_WIDTH, TILE_HEIGHT)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF2C2C2E)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(30.dp))
            Text("New List", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f),
                 modifier = Modifier.padding(top = 8.dp).height(22.dp))
        }
    }
}

/**
 * The first few titles of each list, for its tile, kept per profile for the app's lifetime
 * so scrolling back and forth or switching Movies / Shows doesn't refetch.
 */
private object BrowsePreviews {
    private val cache = mutableMapOf<String, TileArt>()

    suspend fun art(list: BrowseList, version: Int?): TileArt {
        if (list is BrowseList.Similar) return TileArt() // the seed's own backdrop
        val key = "${ApiClient.profileId}|$list|${version ?: ""}"
        cache[key]?.let { return it }
        // Four posters: the quick pass, not a whole remote list read and filled in first.
        val page = runCatching { ApiClient.browsePage(list, limit = 4, phase = ShelfPhase.CARDS) }.getOrNull() ?: return TileArt()
        val art = TileArt(page.entries.mapNotNull(::posterOf), page.entries.mapNotNull(::backdropOf))
        cache[key] = art
        return art
    }

    private fun posterOf(e: BrowseEntry): String? = when (e) {
        is BrowseEntry.TitleEntry -> (e.title.textPoster ?: e.title.poster)?.url
        is BrowseEntry.ItemEntry -> e.item.textPosterUrl ?: e.item.posterUrl
        is BrowseEntry.WatchingEntry -> e.item.textPosterUrl ?: e.item.posterUrl
        is BrowseEntry.LibraryItemEntry -> e.entry.group.posterUrl
    }

    private fun backdropOf(e: BrowseEntry): String? = when (e) {
        is BrowseEntry.TitleEntry -> e.title.backdrop?.url
        is BrowseEntry.ItemEntry -> e.item.backdropUrl
        is BrowseEntry.WatchingEntry -> e.item.backdropUrl
        is BrowseEntry.LibraryItemEntry -> e.entry.group.backdropUrl
    }
}
