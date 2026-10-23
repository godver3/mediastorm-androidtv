package com.strmr.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.strmr.tv.api.Artwork

/**
 * The carousel's backdrop: the slide's art over the last one, faded in once it has loaded.
 * Two images and one alpha read in the draw phase, so a crossfade redraws two layers and
 * recomposes nothing (cheap on a Fire TV). [next] is fetched ahead so auto-advance fades at once.
 */
@Composable
fun CarouselBackdrop(url: String?, next: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shown = Artwork.url(url)
    /** The art under the fading one: the slide before, until the new one is fully in. */
    var under by remember { mutableStateOf(shown) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(shown) {
        if (shown == null) { under = null; fade.snapTo(1f) } else if (shown != under) fade.snapTo(0f)
    }
    LaunchedEffect(next) {
        Artwork.url(next)?.let { context.imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }
    var loaded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(loaded) {
        if (loaded == null || loaded != shown) return@LaunchedEffect
        fade.animateTo(1f, tween(FADE_MS))
        under = shown
    }
    Box(modifier) {
        AsyncImage(model = under, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        AsyncImage(
            model = shown, contentDescription = null, contentScale = ContentScale.Crop,
            onSuccess = { loaded = shown },
            onError = { loaded = shown },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
        )
        CarouselScrim()
    }
}

private const val FADE_MS = 700

/**
 * The legibility scrims, drawn once into a cached layer (as gradient shaders redrawn every
 * frame they cost a Fire TV's GPU more than the rest of Home): a light shade at the top for the
 * tab bar, a partial one at the bottom for the buttons and dots, and the left darkened for the text.
 */
@Composable
private fun CarouselScrim() {
    Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f), 0.18f to Color.Transparent, 0.5f to Color.Transparent,
                    // Only a shade for the buttons and dots: the art runs to the bottom edge, never an empty black band.
                    0.75f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.55f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(0.65f)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.35f), Color.Transparent))),
        )
    }
}

/** The page dots: the current slide a wider white pill, the rest small and dim. */
@Composable
fun CarouselDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val on = i == current
            Box(
                Modifier.height(6.dp).width(if (on) 18.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (on) 0.95f else 0.35f), CircleShape),
            )
        }
    }
}
