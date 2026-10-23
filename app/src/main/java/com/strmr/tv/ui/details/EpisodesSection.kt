package com.strmr.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.ui.cardFocusBorder
import com.strmr.tv.api.Artwork
import com.strmr.tv.api.Episode
import com.strmr.tv.home.ReleaseBadge
import com.strmr.tv.ui.CardFocusScale
import com.strmr.tv.ui.captionGap
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.ContentMargin
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.home.ProgressLine

/**
 * Season chips over the season's episodes. Focus enters the episode row on the one Play
 * targets (else the first), then on whichever was last focused there; select plays it,
 * long-press offers Choose Source and Mark as Watched / Unwatched. Up from any episode goes
 * to the season row (the chip last focused there, else the selected season), as on tvOS,
 * not past it to the actions when no chip sits straight above the episode.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodesSection(
    model: DetailsModel,
    onPlay: (Episode) -> Unit,
    onChooseSource: (Episode) -> Unit,
    onChooseVersion: (Episode) -> Unit,
    onToggleWatched: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuFor by remember { mutableStateOf<Episode?>(null) }
    menuFor?.let { ep ->
        ChoiceDialog(
            title = "${ep.code} · ${ep.name}",
            choices = listOfNotNull(
                DialogChoice("Choose Version") { onChooseVersion(ep) }.takeIf { model.libraryVersions(ep).isNotEmpty() },
                DialogChoice("Choose Source") { onChooseSource(ep) },
                DialogChoice(if (model.isWatched(ep)) "Mark as Unwatched" else "Mark as Watched") { onToggleWatched(ep) },
                DialogChoice("Cancel") {},
            ),
            onDismiss = { menuFor = null },
        )
    }
    val seasons = model.visibleSeasons
    val current = model.currentSeason ?: return
    val seasonRow = remember { FocusRequester() }
    val selectedChip = remember { FocusRequester() }
    val hasSeasonRow = seasons.size > 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasSeasonRow) {
            // Starts on the selected season, so its chip is composed and can take focus.
            val seasonState = rememberLazyListState(initialFirstVisibleItemIndex = seasons.indexOfFirst { it.number == current.number }.coerceAtLeast(0))
            LazyRow(
                state = seasonState,
                contentPadding = PaddingValues(horizontal = ContentMargin, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.focusRequester(seasonRow).focusRestorer(selectedChip),
            ) {
                items(seasons, key = { it.id }) { season ->
                    val selected = season.number == current.number
                    FilterChip(
                        selected = selected, onClick = { model.selectedSeason = season.number },
                        modifier = if (selected) Modifier.focusRequester(selectedChip) else Modifier,
                    ) {
                        Text(if (season.number == 0) "Specials" else "Season ${season.number}")
                    }
                }
            }
        } else {
            Text(current.name.ifEmpty { "Episodes" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = ContentMargin))
        }
        key(current.id) {
            val entryIndex = current.episodes.indexOfFirst { it.id == model.primaryEpisode?.id }.coerceAtLeast(0)
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = entryIndex)
            val entry = remember { FocusRequester() }
            LaunchedEffect(Unit) { listState.scrollToItem(entryIndex) }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = ContentMargin, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.focusRestorer(entry),
            ) {
                items(current.episodes, key = { it.id }) { ep ->
                    EpisodeCard(
                        ep,
                        fallbackImage = model.title?.backdrop?.url ?: model.target.backdropUrl,
                        watched = model.isWatched(ep),
                        inLibrary = model.libraryVersions(ep).isNotEmpty(),
                        // Marking an episode watched or unwatched clears its progress on the server.
                        progress = if (model.isWatched(ep) == (ep.watched == true)) ep.resume?.takeIf { it.eligible }?.fraction ?: 0f else 0f,
                        onClick = { onPlay(ep) },
                        onLongClick = { menuFor = ep },
                        modifier = Modifier
                            .then(if (hasSeasonRow) Modifier.focusProperties { up = seasonRow } else Modifier)
                            .then(if (current.episodes.indexOf(ep) == entryIndex) Modifier.focusRequester(entry) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    fallbackImage: String?,
    watched: Boolean,
    /** The episode has a copy in the profile's libraries (Play uses it). */
    inLibrary: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(Modifier.width(EPISODE_WIDTH), verticalArrangement = Arrangement.spacedBy(captionGap(EPISODE_WIDTH * 9f / 16f))) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(),
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = Artwork.url(episode.image?.url ?: fallbackImage, "w500"),
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1C1C1E)),
                )
                if (watched) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, contentDescription = "Watched", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
                // The air date, released or not, on the still's bottom left (Expo's releaseDateOverlay).
                ReleaseBadge.airDateLabel(episode.airedDate)?.let {
                    Text(
                        it, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = if (progress > 0.01f) 16.dp else 8.dp)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (inLibrary) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = if (progress > 0.01f) 16.dp else 8.dp)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                    ) { Icon(AppIcons.Library, contentDescription = "In your library", tint = Color.White, modifier = Modifier.size(13.dp)) }
                }
                if (progress > 0.01f) ProgressLine(progress, Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${episode.code} · ${episode.name}", fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episode.overview?.let {
                Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val EPISODE_WIDTH = 220.dp
