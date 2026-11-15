package com.strmr.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.home.HomeCard
import com.strmr.tv.ui.rememberVisibleTitleSource
import com.strmr.tv.ui.rememberSourceHint
import com.strmr.tv.ui.SourceHintBadge
import com.strmr.tv.ui.sourceHintArtwork
import com.strmr.tv.api.Artwork
import com.strmr.tv.postplay.rememberPostPlayEditor
import com.strmr.tv.ratings.PersonalRating
import kotlinx.coroutines.launch

/** Native completion panel; its close action keeps focus while suggestions arrive. */
@Composable
fun PostPlayScreen(controller: PlayerController, onClose: () -> Unit) {
    val editor = rememberPostPlayEditor(controller,onClose)
    val state = editor?.state?.collectAsState()?.value
    val scope = rememberCoroutineScope()
    val closeFocus = remember(controller) { FocusRequester() }
    LaunchedEffect(controller) { withFrameNanos { }; runCatching { closeFocus.requestFocus() } }
    BackHandler(onBack = onClose)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 80.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("heading") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (controller.request.isEpisode) "Episode finished" else "Movie finished",style = MaterialTheme.typography.headlineMedium)
                    Text(state?.title?.name ?: controller.request.title.name,maxLines = 2,overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = onClose,modifier = Modifier.focusRequester(closeFocus)) { Text("Close") }
            }
        }
        item("rating") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state == null || state.loading) Text("Loading your profile…")
                state?.error?.let { Text(it); if (!state.saving) Button(onClick = { scope.launch { editor?.load() } }) { Text("Retry") } }
                if (state?.rating != null) {
                    if (state.questionVisible) {
                        Text(if (controller.request.isEpisode) "How do you like this series?" else "Did you enjoy this movie?",style = MaterialTheme.typography.titleLarge)
                        if (controller.request.isEpisode) Text("Your rating is for the whole series")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(enabled = !state.saving,onClick = { scope.launch { editor?.rate(PersonalRating.LIKE) } }) { Text("Yes") }
                            Button(enabled = !state.saving,onClick = { scope.launch { editor?.rate(PersonalRating.DISLIKE) } }) { Text("No") }
                            Button(enabled = !state.saving,onClick = { editor?.skipRating() }) { Text("Skip") }
                            if (!state.expandedRating) Button(enabled = !state.saving,onClick = { editor?.editRating() }) { Text("More choices") }
                        }
                        if (state.expandedRating) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(enabled = !state.saving,onClick = { scope.launch { editor?.rate(PersonalRating.LOVE) } }) { Text("Love it") }
                            Button(enabled = !state.saving,onClick = { scope.launch { editor?.rate(PersonalRating.NONE) } }) { Text("Clear rating") }
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Your rating: ${state.rating?.label.orEmpty()}")
                        Button(enabled = !state.saving,onClick = { editor?.editRating() }) { Text("Change rating") }
                    }
                    if (state.saving) Text("Saving your rating…")
                }
            }
        }
        item("suggestions-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("More for you",style = MaterialTheme.typography.titleLarge)
                if (state?.suggestionsSource == "saved_choices") Text("From your saved choices")
                if (state?.suggestionsLoading == true) Text("Loading suggestions…")
                state?.suggestionsError?.let { Text(it); Button(enabled = !state.saving,onClick = { scope.launch { editor?.load() } }) { Text("Retry suggestions") } }
                if (state != null && !state.loading && !state.suggestionsLoading && state.suggestions.isEmpty() && state.suggestionsError == null) Text("No suggestions available right now")
                state?.listError?.let { Text(it) }
            }
        }
        item("suggestions") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp),contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state?.suggestions.orEmpty(),key = { it.mediaType + ":" + it.id }) { title ->
                    val visibleSource = rememberVisibleTitleSource(HomeCard.from(title),completedPlayback = true)
                    val sourceHint = rememberSourceHint(visibleSource.source ?: title.sourceAvailability)
                    Column(visibleSource.modifier.width(224.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(onClick = { editor?.select(title,false) }) {
                          Box {
                            AsyncImage(model = Artwork.url(title.backdrop?.url ?: title.textPoster?.url ?: title.poster?.url,"w500"),contentDescription = "Details for ${title.name}",
                                contentScale = if (title.backdrop != null) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).background(MaterialTheme.colorScheme.surfaceVariant).sourceHintArtwork(sourceHint))
                            sourceHint?.let { SourceHintBadge(it,Modifier.align(Alignment.TopStart).padding(8.dp)) }
                          }
                        }
                        Text(title.name,maxLines = 2,overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = state?.saving != true,onClick = { editor?.select(title,true) }) { Text("Play") }
                            val key = title.mediaType + ":" + title.id
                            val saved = key in state?.savedToList.orEmpty()
                            Button(enabled = state?.saving != true && key !in state?.listSaving.orEmpty() && !saved,onClick = { scope.launch { editor?.saveToList(title) } }) { Text(if (saved) "Saved" else "My List") }
                        }
                    }
                }
            }
        }
    }
}
