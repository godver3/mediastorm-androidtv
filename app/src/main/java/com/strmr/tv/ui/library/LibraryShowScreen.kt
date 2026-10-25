package com.strmr.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Artwork
import com.strmr.tv.api.LibraryEntry
import com.strmr.tv.api.LibraryEpisode
import com.strmr.tv.api.LibraryGroup
import com.strmr.tv.api.LibrarySeason
import com.strmr.tv.api.libraryGroup
import com.strmr.tv.library.LibraryVersions
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.player.PlaybackRequest
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.pageBackground
import com.strmr.tv.ui.uniqueKeys
import kotlinx.coroutines.launch

/**
 * An unmatched show's library episodes: a show the server couldn't match, so it has no catalog
 * details page. Seasons as chips, episodes as rows; selecting one plays its best copy from where it
 * was left, with the episodes after it as Up Next.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryShowScreen(entry: LibraryEntry) {
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf<LibraryGroup?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var preparing by remember { mutableStateOf<LibraryVersions.Key?>(null) }
    var pending by remember { mutableStateOf<LibraryFileStart?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(entry.id) {
        try {
            group = ApiClient.libraryGroup(entry)
        } catch (e: Exception) {
            loadError = e.message
        }
    }
    val loaded = group
    val versions = remember(loaded) { loaded?.let { LibraryVersions.of(it, entry.library.name) } }
    // Real seasons first, specials last.
    val seasons = loaded?.seasons.orEmpty().sortedBy { it.seasonNumber }.let { all -> all.filter { it.seasonNumber > 0 } + all.filter { it.seasonNumber == 0 } }
    val season = seasons.firstOrNull { it.seasonNumber == selected } ?: seasons.firstOrNull()
    LaunchedEffect(season != null) { if (season != null) runCatching { first.requestFocus() } }

    fun name(episode: LibraryEpisode) = episode.episodeTitle?.ifEmpty { null } ?: "Episode ${episode.episodeNumber}"
    fun info(s: LibrarySeason, e: LibraryEpisode) =
        PlaybackRequest.EpisodeInfo(s.seasonNumber, e.episodeNumber, name = name(e), imageUrl = e.episodeImage?.url, overview = e.episodeOverview)

    fun play(s: LibrarySeason, e: LibraryEpisode) {
        val key = LibraryVersions.Key(s.seasonNumber, e.episodeNumber)
        val all = versions ?: return
        val version = all.best(s.seasonNumber, e.episodeNumber) ?: return
        if (preparing != null || loaded == null) return
        // The regular-season episodes after this one, in order, for Up Next.
        val ordered = seasons.filter { it.seasonNumber > 0 }.flatMap { x -> x.episodes.sortedBy { it.episodeNumber }.map { x to it } }
        val index = ordered.indexOfFirst { it.first.seasonNumber == s.seasonNumber && it.second.episodeNumber == e.episodeNumber }
        val following = if (index < 0) emptyList() else ordered.drop(index + 1).map { info(it.first, it.second) }
        preparing = key
        scope.launch {
            try {
                val start = LibraryFileStart.prepare(version, all, loaded, info(s, e), following)
                if (start.resumeAt != null) pending = start else start.play(null)
            } catch (ex: Exception) {
                PlaybackCoordinator.errorMessage.value = ex.message ?: "Playback failed"
            } finally {
                preparing = null
            }
        }
    }

    Box(Modifier.fillMaxSize().pageBackground()) {
        LazyColumn(
            Modifier.widthIn(max = 900.dp),
            contentPadding = PaddingValues(horizontal = PageMargin, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.group.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("${com.strmr.tv.api.LibrarySource.of(entry.library.sourceType).label} · ${entry.library.name}", color = Color.White.copy(alpha = 0.6f))
                    when {
                        loadError != null -> Text(loadError!!, color = Color(0xFFFF6B6B))
                        loaded == null -> CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    }
                }
            }
            if (seasons.size > 1) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(seasons, key = { it.seasonNumber }) { s ->
                        FilterChip(selected = s.seasonNumber == season?.seasonNumber, onClick = { selected = s.seasonNumber }) {
                            Text(if (s.seasonNumber == 0) "Specials" else "Season ${s.seasonNumber}")
                        }
                    }
                }
            }
            season?.let { s ->
                // Unnumbered extras share episode number 0, so the key needs the group ID.
                val episodes = s.episodes.sortedBy { it.episodeNumber }
                val keys = uniqueKeys(episodes) { it.id ?: "${s.seasonNumber}-${it.episodeNumber}" }
                itemsIndexed(episodes, key = { i, _ -> keys[i] }) { _, e ->
                    ListItem(
                        selected = false,
                        onClick = { play(s, e) },
                        leadingContent = {
                            AsyncImage(
                                Artwork.url(e.episodeImage?.url ?: entry.group.backdropUrl, "w300"), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1C1C1E)),
                            )
                        },
                        headlineContent = { Text("${e.episodeNumber}. ${name(e)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = e.episodeOverview?.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                        trailingContent = if (preparing == LibraryVersions.Key(s.seasonNumber, e.episodeNumber)) {
                            { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) }
                        } else null,
                        modifier = if (e == s.episodes.minByOrNull { it.episodeNumber }) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
    pending?.let { LibraryResumeDialog(it) { pending = null } }
}
