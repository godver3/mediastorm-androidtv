package com.strmr.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.ui.AppIcons

/** Glass by default, white with dark content on focus (the tvOS transport's buttons). */
@Composable
private fun pillColors() = ClickableSurfaceDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.14f),
    contentColor = Color.White,
    focusedContainerColor = Color.White,
    focusedContentColor = Color.Black,
    pressedContainerColor = Color.White.copy(alpha = 0.85f),
    pressedContentColor = Color.Black,
)

/** A round icon button of the transport row. */
@Composable
fun TransportButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(52.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = pillColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp)) }
    }
}

/**
 * Skip Intro / Skip Credits / Next Episode, at the bottom right, over the transport or alone.
 * Next Episode with a countdown fills from the left as it runs down.
 */
@Composable
fun SkipPill(action: SkipAction, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = pillColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Row(Modifier.padding(horizontal = 26.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (action is SkipAction.NextEpisode) Icon(AppIcons.SkipNext, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(action.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The Info panel (the tvOS player's swipe-down Info tab): the episode still or backdrop, the
 * title, the episode and the overview. Focusable, so Back or Up / Down close it.
 */
@Composable
fun InfoPanel(request: PlaybackRequest, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // The episode still, else a backdrop; a poster (letterboxed) only when there's no 16:9 art.
    val wide = request.episode?.imageUrl ?: request.backdrops.firstOrNull() ?: request.title.backdropUrl
    val image = wide ?: request.title.posterUrl?.takeIf { it.isNotEmpty() }
    val overview = request.episode?.overview ?: request.title.overview
    Row(
        modifier.fillMaxWidth().shadow(24.dp, PanelShape).background(Color(0xF2242426), PanelShape)
            .focusRequester(focus).focusable().padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        image?.let {
            AsyncImage(
                model = Artwork.url(it), contentDescription = null, contentScale = if (wide != null) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.width(320.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(request.title.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            request.episode?.let { ep ->
                Text(listOfNotNull(ep.code, ep.name?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                     style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f))
            }
            request.title.year?.takeIf { request.episode == null }?.let {
                Text(it.toString(), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f))
            }
            overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.85f),
                     modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/**
 * Over a live channel while it is tuned again ([LiveRetune]): its logo and name and
 * "Reconnecting…" on black, like the tvOS player's loading cover. Not focusable.
 */
@Composable
fun LiveReconnectingCover(request: PlaybackRequest, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        // A logo that doesn't load leaves the cover, rather than an empty box above the name (as tvOS hides it).
        var logoFailed by remember { mutableStateOf(false) }
        Artwork.logo(request.logoUrl ?: request.title.logoUrl)?.takeIf { !logoFailed }?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit,
                       onError = { logoFailed = true }, modifier = Modifier.size(320.dp, 140.dp))
        }
        Text(request.title.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
             maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 64.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            Text("Reconnecting…", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

private val PanelShape = RoundedCornerShape(20.dp)
