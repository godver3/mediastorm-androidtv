package com.strmr.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.strmr.tv.api.Artwork
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLayoutStore
import com.strmr.tv.ui.ContentMargin

/**
 * The slide's text, anchored just above the controls. Each slide is one natural-height column
 * (rank line, logo or name, metadata, overview; a part the title lacks takes no room), and a
 * new slide crossfades in over the last with their bottom edges lined up, so a change in
 * height moves nothing. One alpha read in the draw phase; the next slide's logo is fetched ahead.
 */
@Composable
fun CarouselInfo(slides: List<HomeCard>, state: CarouselState, rowTitle: String?, modifier: Modifier) {
    val slide = slides.getOrNull(state.index) ?: return
    val scale = HomeLayoutStore.heroTextSize.scale
    val context = LocalContext.current
    val next = slides.getOrNull((state.index + 1) % slides.size)?.let { Artwork.logo(it.logoUrl) }
    LaunchedEffect(next) { next?.let { context.imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) } }
    var shown by remember { mutableStateOf(slide) }
    /** The slide fading out under [shown], until it is fully in. */
    var under by remember { mutableStateOf<HomeCard?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(slide) {
        if (slide == shown) return@LaunchedEffect
        under = shown
        shown = slide
        fade.snapTo(0f)
        fade.animateTo(1f, tween(FADE_MS))
        under = null
    }
    Box(
        modifier.padding(start = ContentMargin, end = ContentMargin, bottom = SAFE_BOTTOM + CONTROLS_HEIGHT + (8 * scale).dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        // Keyed, so the slide fading out keeps its composition (its logo drawn, not reloaded).
        for (card in listOfNotNull(under?.takeIf { it != shown }, shown)) key(card) {
            val incoming = card == shown
            SlideInfo(card, rowTitle, scale, Modifier.graphicsLayer { alpha = if (incoming) fade.value else 1f - fade.value })
        }
    }
}

private const val FADE_MS = 450

/** Rank line, logo (else the name), the metadata line and the synopsis. */
@Composable
private fun SlideInfo(slide: HomeCard, rowTitle: String?, scale: Float, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
        slide.rank?.let { rank ->
            Text(
                rowTitle?.let { "#$rank in $it" } ?: "#$rank", fontSize = (13 * scale).sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, color = Color.White.copy(alpha = 0.7f),
            )
        }
        val logo = Artwork.logo(slide.logoUrl)
        if (logo != null) SlideLogo(logo, slide.title, scale) else SlideTitle(slide.title, scale)
        metaLine(slide).takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = (15 * scale).sp, fontWeight = FontWeight.Medium, maxLines = 1, color = Color.White.copy(alpha = 0.9f))
        }
        slide.overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                it, fontSize = (13 * scale).sp, color = Color.White.copy(alpha = 0.75f), maxLines = 3,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = (480 * maxOf(scale, 1f)).dp),
            )
        }
    }
}

/**
 * The logo at its own shape, as large as fits the 400 x 96 box: a low, wide logo takes only
 * its own height. Holds the box's height while loading; the name if it fails.
 */
@Composable
private fun SlideLogo(url: String, title: String, scale: Float) {
    val boxW = 400f * scale
    val boxH = 96f * scale
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(url, scale) {
        with(density) { ImageRequest.Builder(context).data(url).size(boxW.dp.roundToPx(), boxH.dp.roundToPx()).build() }
    }
    SubcomposeAsyncImage(model = request, contentDescription = title) {
        when (val s = painter.state) {
            is AsyncImagePainter.State.Success -> {
                val px = s.painter.intrinsicSize
                if (px.isSpecified && px.width > 0f && px.height > 0f) {
                    val fit = minOf(boxW / px.width, boxH / px.height)
                    Image(painter, title, contentScale = ContentScale.FillBounds, modifier = Modifier.size((px.width * fit).dp, (px.height * fit).dp))
                } else {
                    SlideTitle(title, scale)
                }
            }
            is AsyncImagePainter.State.Error -> SlideTitle(title, scale)
            else -> Spacer(Modifier.height(boxH.dp))
        }
    }
}

@Composable
private fun SlideTitle(title: String, scale: Float) {
    Text(title, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
         modifier = Modifier.widthIn(max = 520.dp))
}

/** "2024 · Movie · PG-13 · Drama" (the rank has its own line). */
internal fun metaLine(slide: HomeCard): String = listOfNotNull(
    slide.subtitle.takeIf { it.isNotBlank() },
    slide.certification,
    slide.genre,
).joinToString(" · ")
