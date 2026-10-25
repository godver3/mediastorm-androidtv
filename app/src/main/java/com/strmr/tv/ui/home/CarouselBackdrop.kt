package com.strmr.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
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
 * The carousel's backdrop: a stack of slide art, each new slide fading in over whatever is on
 * screen once it has loaded; a layer fully in drops the ones beneath. Rapid presses fade from
 * the half-faded art, never back to the slide that was up first. Each layer keeps its own image
 * (nothing reloads mid-fade) and alpha read in the draw phase, so a crossfade recomposes nothing
 * (cheap on a Fire TV). [next] is fetched ahead so auto-advance fades at once.
 */
@Composable
fun CarouselBackdrop(url: String?, next: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shown = Artwork.url(url)
    val layers = remember { mutableStateListOf<BackdropLayer>() }
    LaunchedEffect(shown) {
        if (shown == null) { layers.clear(); return@LaunchedEffect }
        if (layers.lastOrNull()?.url == shown) return@LaunchedEffect
        // Layers not yet fading in show nothing; past that, cap the full-screen layers drawn at once.
        val bottom = layers.firstOrNull()
        layers.removeAll { it !== bottom && it.alpha.value == 0f }
        while (layers.size >= MAX_LAYERS) layers.removeAt(1)
        // The first art shows as it loads; later ones start hidden, so a preloaded one never draws at full alpha first.
        layers += BackdropLayer(shown, Animatable(if (layers.isEmpty()) 1f else 0f))
    }
    LaunchedEffect(next) {
        Artwork.url(next)?.let { context.imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }
    Box(modifier) {
        for (layer in layers) key(layer) {
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(loaded) {
                if (!loaded) return@LaunchedEffect
                layer.alpha.animateTo(1f, tween(FADE_MS))
                val index = layers.indexOf(layer)
                if (index > 0) layers.removeRange(0, index)
            }
            AsyncImage(
                model = layer.url, contentDescription = null, contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = true },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = layer.alpha.value },
            )
        }
        CarouselScrim()
    }
}

/** One slide's art in the backdrop stack, with its fade. */
private class BackdropLayer(val url: String, val alpha: Animatable<Float, AnimationVector1D>)

/** The settled art, one slide fading in, and one caught mid-fade by a quick press. */
private const val MAX_LAYERS = 3
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
