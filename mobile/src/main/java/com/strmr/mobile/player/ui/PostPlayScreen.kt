package com.strmr.mobile.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.strmr.tv.home.HomeCard
import com.strmr.tv.ui.rememberVisibleTitleSource
import com.strmr.tv.ui.rememberSourceHint
import com.strmr.tv.ui.SourceHintBadge
import com.strmr.tv.ui.sourceHintArtwork
import com.strmr.tv.api.Artwork
import com.strmr.tv.player.PlayerController
import com.strmr.tv.postplay.rememberPostPlayEditor
import com.strmr.tv.ratings.PersonalRating
import kotlinx.coroutines.launch

@Composable
fun PostPlayScreen(controller: PlayerController, onClose: () -> Unit) {
    val editor = rememberPostPlayEditor(controller,onClose)
    val state = editor?.state?.collectAsState()?.value
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item("heading") {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (controller.request.isEpisode) "Episode finished" else "Movie finished",style = MaterialTheme.typography.headlineMedium)
                    Text(state?.title?.name ?: controller.request.title.name,maxLines = 2,overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
        item("rating") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state == null || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state?.error?.let { Text(it,color = MaterialTheme.colorScheme.error); if (!state.saving) TextButton(onClick = { scope.launch { editor?.load() } }) { Text("Retry") } }
                if (state?.rating != null) {
                    if (state.questionVisible) {
                        Text(if (controller.request.isEpisode) "How do you like this series?" else "Did you enjoy this movie?",style = MaterialTheme.typography.titleLarge)
                        if (controller.request.isEpisode) Text("Your rating is for the whole series")
                        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !state.saving,onClick = { scope.launch { editor?.rate(PersonalRating.LIKE) } },modifier = Modifier.weight(1f)) { Text("Yes") }
                            OutlinedButton(enabled = !state.saving,onClick = { scope.launch { editor?.rate(PersonalRating.DISLIKE) } },modifier = Modifier.weight(1f)) { Text("No") }
                            TextButton(enabled = !state.saving,onClick = { editor?.skipRating() },modifier = Modifier.weight(1f)) { Text("Skip") }
                        }
                        if (state.expandedRating) {
                            TextButton(enabled = !state.saving,onClick = { scope.launch { editor?.rate(PersonalRating.LOVE) } }) { Text("Love it") }
                            TextButton(enabled = !state.saving,onClick = { scope.launch { editor?.rate(PersonalRating.NONE) } }) { Text("Clear rating") }
                        } else TextButton(enabled = !state.saving,onClick = { editor?.editRating() }) { Text("More choices") }
                    } else {
                        Text("Your rating: ${state.rating?.label.orEmpty()}")
                        TextButton(enabled = !state.saving,onClick = { editor?.editRating() }) { Text("Change rating") }
                    }
                    if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        item("suggestions-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("More for you",style = MaterialTheme.typography.titleLarge)
                if (state?.suggestionsSource == "saved_choices") Text("From your saved choices")
                if (state?.suggestionsLoading == true) LinearProgressIndicator(Modifier.fillMaxWidth())
                state?.suggestionsError?.let { Text(it); TextButton(enabled = !state.saving,onClick = { scope.launch { editor?.load() } }) { Text("Retry suggestions") } }
                if (state != null && !state.loading && !state.suggestionsLoading && state.suggestions.isEmpty() && state.suggestionsError == null) Text("No suggestions available right now")
                state?.listError?.let { Text(it,color = MaterialTheme.colorScheme.error) }
            }
        }
        item("suggestions") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state?.suggestions.orEmpty(),key = { it.mediaType + ":" + it.id }) { title ->
                    val visibleSource = rememberVisibleTitleSource(HomeCard.from(title),completedPlayback = true)
                    val sourceHint = rememberSourceHint(visibleSource.source ?: title.sourceAvailability)
                    Column(visibleSource.modifier.width(208.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(onClick = { editor?.select(title,false) }) {
                          Box {
                            AsyncImage(model = Artwork.url(title.backdrop?.url ?: title.textPoster?.url ?: title.poster?.url,"w500"),contentDescription = "Details for ${title.name}",
                                contentScale = if (title.backdrop != null) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).background(MaterialTheme.colorScheme.surfaceVariant).sourceHintArtwork(sourceHint))
                            sourceHint?.let { SourceHintBadge(it,Modifier.align(Alignment.TopStart).padding(8.dp)) }
                          }
                        }
                        Text(title.name,maxLines = 2,overflow = TextOverflow.Ellipsis,style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = state?.saving != true,onClick = { editor?.select(title,true) }) { Text("Play") }
                            val key = title.mediaType + ":" + title.id
                            val saved = key in state?.savedToList.orEmpty()
                            OutlinedButton(enabled = state?.saving != true && key !in state?.listSaving.orEmpty() && !saved,onClick = { scope.launch { editor?.saveToList(title) } }) { Text(if (saved) "Saved" else "My List") }
                        }
                    }
                }
            }
        }
    }
}
