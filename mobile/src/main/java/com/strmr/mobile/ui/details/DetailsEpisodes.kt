package com.strmr.mobile.ui.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.strmr.mobile.ui.components.ProgressLine
import com.strmr.mobile.ui.components.cardClicks
import com.strmr.mobile.ui.components.showsProgress
import com.strmr.tv.api.Artwork
import com.strmr.tv.api.Episode
import com.strmr.tv.api.formatRuntime
import com.strmr.tv.home.ReleaseBadge
import com.strmr.tv.ui.details.DetailsModel
import com.strmr.tv.ui.details.EpisodePrivacy
import com.strmr.tv.ui.details.blurredStill
import com.strmr.tv.ui.details.blurredStillRequest
import com.strmr.tv.ui.details.blurredTextStyle
import com.strmr.tv.ui.details.hiddenOverviewSemantics
import com.strmr.tv.ui.settings.SpoilerSettings

private val EPISODE_WIDTH = 280.dp
private val StillShape = RoundedCornerShape(10.dp)

/**
 * Series: season chips (with episode counts) over the season's episodes in a sideways row,
 * as the Expo and iPhone pages. Tapping an episode selects it as what Play targets; tapping the
 * selected one plays it. Long press lists the episode's actions.
 */
@Composable
fun EpisodesSection(state: DetailsPageState) {
    val model = state.model
    val seasons = model.visibleSeasons
    val current = model.currentSeason ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (seasons.size > 1) {
            val chips = rememberLazyListState(initialFirstVisibleItemIndex = seasons.indexOfFirst { it.number == current.number }.coerceAtLeast(0))
            LazyRow(state = chips, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Keyed by number: a season's ID can be empty, and a repeated key crashes the row.
                items(seasons, key = { it.number }) { season ->
                    FilterChip(
                        selected = season.number == current.number,
                        onClick = { model.selectedSeason = season.number },
                        label = { Text("${seasonName(season.number)}  ${season.episodes.size}") },
                    )
                }
            }
        } else {
            Text(current.name.ifEmpty { "Episodes" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                 modifier = Modifier.padding(horizontal = 16.dp))
        }
        key(current.number) {
            val active = state.activeEpisode
            val start = current.episodes.indexOfFirst { it.id == active?.id }.coerceAtLeast(0)
            val list = rememberLazyListState(initialFirstVisibleItemIndex = start)
            LaunchedEffect(Unit) { list.scrollToItem(start) }
            val privacy = SpoilerSettings.effective
            LazyRow(state = list, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(current.episodes, key = { "${it.seasonNumber}x${it.episodeNumber}:${it.id}" }) { ep ->
                    val watched = model.isWatched(ep)
                    EpisodeCard(
                        ep,
                        fallbackImage = model.title?.backdrop?.url ?: model.target.backdropUrl,
                        watched = watched,
                        selected = ep.id == active?.id,
                        blurStill = EpisodePrivacy.shouldBlur(ep, model.privacyEpisode, privacy.blurThumbnails, privacy.blurThumbnailsIncludeCurrent, watched) &&
                            // The series backdrop standing in for an unaired episode's still gives nothing away.
                            !(ep.image?.url == null && !DetailsModel.hasAired(ep)),
                        blurOverview = EpisodePrivacy.shouldBlur(ep, model.privacyEpisode, privacy.blurOverviews, privacy.blurOverviewsIncludeCurrent, watched),
                        inLibrary = model.libraryVersions(ep).isNotEmpty(),
                        progress = episodeProgress(model, ep),
                        onClick = { state.tapEpisode(ep) },
                        onLongClick = { state.episodeMenu = ep },
                    )
                }
            }
        }
    }
}

/** An episode's resume progress; marking it watched or unwatched here clears it on the server. */
fun episodeProgress(model: DetailsModel, ep: Episode): Float =
    if (model.isWatched(ep) == (ep.watched == true)) ep.resume?.takeIf { it.eligible }?.fraction ?: 0f else 0f

@Composable
private fun EpisodeCard(
    episode: Episode,
    fallbackImage: String?,
    watched: Boolean,
    selected: Boolean,
    blurStill: Boolean,
    blurOverview: Boolean,
    inLibrary: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier.width(EPISODE_WIDTH).clip(StillShape).cardClicks(onClick, onLongClick).semantics { this.selected = selected },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(StillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), StillShape) else Modifier),
        ) {
            val still = Artwork.url(episode.image?.url ?: fallbackImage, "w500")
            AsyncImage(
                model = if (blurStill) blurredStillRequest(still) else still,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (blurStill) Modifier.blurredStill() else Modifier),
            )
            Text(
                "${episode.episodeNumber}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            StatusBadge(episode, watched, progress, Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (inLibrary) {
                Icon(
                    Icons.Filled.Storage, contentDescription = "In your library", tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = if (showsProgress(progress)) 16.dp else 8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape).padding(5.dp).size(12.dp),
                )
            }
            if (showsProgress(progress)) ProgressLine(progress, Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Text(
            episode.name.ifEmpty { "Episode ${episode.episodeNumber}" }, style = MaterialTheme.typography.titleSmall,
            color = if (watched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        episode.overview?.takeIf { it.isNotEmpty() }?.let {
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            if (blurOverview) {
                Text(it, style = MaterialTheme.typography.bodySmall.merge(blurredTextStyle(color)), maxLines = 3, overflow = TextOverflow.Ellipsis,
                     modifier = Modifier.hiddenOverviewSemantics())
            } else {
                Text(it, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        val meta = listOfNotNull(formatRuntime(episode.runtimeMinutes), ReleaseBadge.airDateLabel(episode.airedDate)).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
    }
}

/** Progress as a percentage, else a watched check, else the unreleased date. */
@Composable
private fun StatusBadge(episode: Episode, watched: Boolean, progress: Float, modifier: Modifier) {
    val unreleased = ReleaseBadge.forEpisode(episode.airedDate)
    when {
        showsProgress(progress) -> Text(
            "${Math.round(progress * 100)}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        watched -> Icon(
            Icons.Filled.Check, contentDescription = "Watched", tint = MaterialTheme.colorScheme.onPrimary,
            modifier = modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp).size(14.dp),
        )
        unreleased != null -> Text(
            unreleased, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Black,
            modifier = modifier.background(Color.White, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
