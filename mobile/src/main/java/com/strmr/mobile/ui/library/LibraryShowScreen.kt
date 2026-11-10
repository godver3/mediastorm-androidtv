package com.strmr.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.ArtImage
import com.strmr.mobile.ui.components.CardShape
import com.strmr.mobile.ui.details.seasonName
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.LibraryEntry
import com.strmr.tv.api.LibraryEpisode
import com.strmr.tv.api.LibraryGroup
import com.strmr.tv.api.LibrarySeason
import com.strmr.tv.api.LibrarySource
import com.strmr.tv.api.libraryGroup
import com.strmr.tv.library.LibraryVersions
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.player.PlaybackRequest
import com.strmr.tv.ui.library.LibraryFileStart
import com.strmr.tv.ui.uniqueKeys
import kotlinx.coroutines.launch

/**
 * An unmatched show's library episodes (a show the server couldn't match, so it has no catalog
 * details page): seasons as chips, episodes as rows. Tapping one plays its best copy, asking
 * Resume / Start Over when it was left partway, with the episodes after it as Up Next.
 */
@Composable
fun LibraryShowScreen(entry: LibraryEntry) {
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf<LibraryGroup?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var preparing by remember { mutableStateOf<LibraryVersions.Key?>(null) }
    var pending by remember { mutableStateOf<LibraryFileStart?>(null) }
    LaunchedEffect(entry.id) {
        try {
            group = ApiClient.libraryGroup(entry)
        } catch (e: Exception) {
            loadError = e.message ?: "Couldn't load this show."
        }
    }
    val loaded = group
    val versions = remember(loaded) { loaded?.let { LibraryVersions.of(it, entry.library.name) } }
    // Real seasons first, specials last.
    val seasons = loaded?.seasons.orEmpty().sortedBy { it.seasonNumber }.let { all -> all.filter { it.seasonNumber > 0 } + all.filter { it.seasonNumber == 0 } }
    val season = seasons.firstOrNull { it.seasonNumber == selected } ?: seasons.firstOrNull()

    fun name(e: LibraryEpisode) = e.episodeTitle?.ifEmpty { null } ?: "Episode ${e.episodeNumber}"
    fun info(s: LibrarySeason, e: LibraryEpisode) =
        PlaybackRequest.EpisodeInfo(s.seasonNumber, e.episodeNumber, name = name(e), imageUrl = e.episodeImage?.url, overview = e.episodeOverview)

    fun play(s: LibrarySeason, e: LibraryEpisode) {
        val all = versions ?: return
        val version = all.best(s.seasonNumber, e.episodeNumber) ?: return
        if (preparing != null || loaded == null) return
        // The regular-season episodes after this one, in order, for Up Next.
        val ordered = seasons.filter { it.seasonNumber > 0 }.flatMap { x -> x.episodes.sortedBy { it.episodeNumber }.map { x to it } }
        val index = ordered.indexOfFirst { it.first.seasonNumber == s.seasonNumber && it.second.episodeNumber == e.episodeNumber }
        val following = if (index < 0) emptyList() else ordered.drop(index + 1).map { info(it.first, it.second) }
        preparing = LibraryVersions.Key(s.seasonNumber, e.episodeNumber)
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

    PageScaffold(entry.group.title, showBack = true) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${LibrarySource.of(entry.library.sourceType).label} · ${entry.library.name}",
                         style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        loadError != null -> Text(loadError!!, color = MaterialTheme.colorScheme.error)
                        loaded == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
            if (seasons.size > 1) item(key = "seasons") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons, key = { it.seasonNumber }) { s ->
                        FilterChip(selected = s.seasonNumber == season?.seasonNumber, onClick = { selected = s.seasonNumber },
                                   label = { Text(seasonName(s.seasonNumber)) })
                    }
                }
            }
            season?.let { s ->
                // Unnumbered extras share episode number 0, so the key needs the group ID.
                val episodes = s.episodes.sortedBy { it.episodeNumber }
                val keys = uniqueKeys(episodes) { it.id ?: "${s.seasonNumber}-${it.episodeNumber}" }
                itemsIndexed(episodes, key = { i, _ -> keys[i] }) { _, e ->
                    ListItem(
                        leadingContent = {
                            ArtImage(e.episodeImage?.url ?: entry.group.backdropUrl, "w300", null,
                                     Modifier.size(112.dp, 63.dp).clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                        },
                        headlineContent = { Text("${e.episodeNumber}. ${name(e)}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = e.episodeOverview?.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                        trailingContent = if (preparing == LibraryVersions.Key(s.seasonNumber, e.episodeNumber)) {
                            { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else null,
                        modifier = Modifier.clickable { play(s, e) },
                    )
                }
            }
        }
    }
    pending?.let { LibraryResumeDialog(it) { pending = null } }
}
