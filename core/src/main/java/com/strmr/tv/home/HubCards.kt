package com.strmr.tv.home

import com.strmr.tv.ui.lists.BrowsePreviews
import com.strmr.tv.ui.lists.TileArt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Collection hub cards. A link without its own art or logo shows a preview of the list it
 * opens, as the Expo home does: a 2×2 of its first titles' art, or one backdrop when fewer
 * than four have art. Only a list that gives no art at all falls back to the name tile.
 */
object HubCards {
    suspend fun cards(links: List<HomeSource.HubLink>, cap: Int): List<HomeCard> = coroutineScope {
        // The shown links' previews, all in flight at once, from the Lists tab's per-profile cache.
        val previews = links.take(cap).map { link ->
            async { if (link.art == null && link.logo == null) BrowsePreviews.art(link.list, null) else null }
        }.awaitAll()
        links.mapIndexed { i, link -> card(link, previews.getOrNull(i)) }
    }

    fun card(link: HomeSource.HubLink, preview: TileArt?): HomeCard {
        val backdrops = preview?.backdrops.orEmpty()
        val posters = preview?.posters.orEmpty()
        val collage = when {
            backdrops.size >= 4 -> backdrops.take(4)
            posters.size >= 4 -> posters.take(4)
            else -> emptyList()
        }
        val backdrop = link.art ?: backdrops.firstOrNull()
        return HomeCard(
            "hub-${link.id}", HomeLink.OpenList(link.list), link.name, link.list.title, backdropUrl = backdrop,
            logoUrl = link.logo, logoTile = backdrop == null && collage.isEmpty(), collageUrls = collage,
        )
    }
}
