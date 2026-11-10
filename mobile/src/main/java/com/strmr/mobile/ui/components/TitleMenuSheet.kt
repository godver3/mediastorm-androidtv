package com.strmr.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.LibraryEntry
import com.strmr.tv.api.isEpisodeWatched
import com.strmr.tv.api.isMovieWatched
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.SetAsideStore
import com.strmr.tv.library.TitleActions
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.ui.AppToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One row of a menu sheet. */
data class SheetAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val action: () -> Unit)

/**
 * The title menu's sheet, in the Expo app's order (as the TV menu): Hide from Continue Watching
 * and Set Aside on a Continue Watching card, Remove from Set Aside, Go to Details, the watchlist
 * toggle, Add to List…, Mark as watched / unwatched for a movie or the card's one episode (its
 * state read when the sheet opens), Watch State… for a series, and Hide from this profile (Unhide
 * when hidden). A library title that has no details page offers Play / Open instead.
 */
@Composable
internal fun TitleMenuSheet(
    card: HomeCard,
    continueWatching: Boolean,
    scope: CoroutineScope,
    openLibrary: (LibraryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val target = card.target
    var watched by remember { mutableStateOf<Boolean?>(null) }
    val episode = card.episode
    LaunchedEffect(card.id) {
        if (target == null) return@LaunchedEffect
        // Continue Watching's next episode is unwatched by definition.
        watched = when {
            target.isMovie -> runCatching { ApiClient.isMovieWatched(target) }.getOrNull()
            episode != null && !continueWatching -> runCatching { ApiClient.isEpisodeWatched(episode, target) }.getOrNull()
            else -> null
        }
        SetAsideStore.refresh()
    }
    fun act(action: suspend () -> Unit) = scope.launch { action() }

    val actions = buildList {
        val library = card.link as? HomeLink.Library
        if (library != null) {
            add(SheetAction(if (library.entry.group.isShow) "Open" else "Play", Icons.Outlined.PlayArrow) { openLibrary(library.entry) })
        }
        if (target == null) return@buildList
        if (continueWatching) {
            add(SheetAction("Hide from Continue Watching", Icons.Outlined.RemoveCircleOutline) { act { TitleActions.hideFromContinueWatching(target) } })
            add(SheetAction("Set Aside", Icons.Outlined.PauseCircle) { act { TitleActions.setAside(target) } })
        }
        if (SetAsideStore.contains(target)) {
            add(SheetAction("Remove from Set Aside", Icons.Outlined.PauseCircle) { act { TitleActions.removeFromSetAside(target) } })
        }
        add(SheetAction("Go to Details", Icons.Outlined.Info) { navigator.openDetails(target) })
        val inWatchlist = WatchlistStore.contains(target)
        add(SheetAction(if (inWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                        if (inWatchlist) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd) {
            act { runCatching { WatchlistStore.toggle(target) }.onFailure { AppToast.show(it.message ?: "Couldn't update the watchlist") } }
        })
        add(SheetAction("Add to List…", Icons.AutoMirrored.Outlined.PlaylistAdd) { CustomListsStore.picking = target })
        val isWatched = watched == true
        if (target.isMovie) {
            // A partly watched movie (Continue Watching) or an unknown state offers Mark as watched.
            add(SheetAction(if (isWatched) "Mark as Unwatched" else "Mark as Watched", Icons.Outlined.CheckCircle) {
                act { TitleActions.setMovieWatched(target, !isWatched) }
            })
        } else {
            if (episode != null) {
                add(SheetAction(if (isWatched) "Mark ${episode.code} as Unwatched" else "Mark ${episode.code} as Watched", Icons.Outlined.CheckCircle) {
                    act { TitleActions.setEpisodeWatched(target, episode, !isWatched) }
                })
            }
            add(SheetAction("Watch State…", Icons.Outlined.Checklist) { TitleMenu.watchStateFor = target })
        }
        if (HiddenItemsStore.isHidden(target)) {
            add(SheetAction("Unhide", Icons.Outlined.Visibility) { act { TitleActions.unhide(target) } })
        } else {
            add(SheetAction("Hide from This Profile", Icons.Outlined.VisibilityOff, destructive = true) { act { TitleActions.hide(target) } })
        }
    }
    ActionSheet(onDismiss, header = { SheetHeader(card.title, card.subtitle, card.posterUrl ?: card.artworkUrl) }, actions = actions)
}

/**
 * A Material bottom sheet of [actions] under a [header]; choosing one closes the sheet first,
 * then runs it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(onDismiss: () -> Unit, header: @Composable () -> Unit, actions: List<SheetAction>) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            header()
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            actions.forEach { item ->
                val color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ListItem(
                    headlineContent = { Text(item.label) },
                    leadingContent = { Icon(item.icon, contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent, headlineColor = color,
                        leadingIconColor = if (item.destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.clickable {
                        scope.launch { state.hide() }.invokeOnCompletion {
                            onDismiss()
                            item.action()
                        }
                    },
                )
            }
        }
    }
}

/** A sheet's header: the title's small poster (when it has one), name and line. */
@Composable
fun SheetHeader(title: String, subtitle: String?, imageUrl: String?) {
    Row(
        Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imageUrl != null) {
            ArtImage(imageUrl, "w185", null, Modifier.size(44.dp, 66.dp).clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                     overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
