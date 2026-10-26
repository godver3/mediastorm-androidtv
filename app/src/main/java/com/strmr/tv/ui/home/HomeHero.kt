package com.strmr.tv.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.strmr.tv.api.Artwork
import com.strmr.tv.home.HeroSize
import kotlinx.coroutines.delay

/** What the hero shows for the focused card (or explore card). */
data class HeroContent(
    val title: String,
    val subtitle: String,
    val overview: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val drawable: Int? = null,
)

/** The full-bleed backdrop that follows focus, darker at the bottom for the rows and at the left for the text. */
@Composable
fun HeroBackdrop(hero: HeroContent?) {
    Box(Modifier.fillMaxSize()) {
        if (hero?.drawable != null) {
            Image(painterResource(hero.drawable), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(settledArt(Artwork.url(hero?.backdropUrl))).crossfade(350).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        HeroScrim()
    }
}

/**
 * [url] once focus has rested on it for [ART_SETTLE_MS], or at once when it is already in
 * memory: a fast D-pad sweep then fetches and decodes a full-screen backdrop only where it
 * stops, not one per card it passes.
 */
@Composable
private fun settledArt(url: String?): String? {
    val context = LocalContext.current
    var settled by remember { mutableStateOf(url) }
    LaunchedEffect(url) {
        val inMemory = context.imageLoader.memoryCache?.keys?.any { it.key == url } == true
        if (url != null && !inMemory) delay(ART_SETTLE_MS)
        settled = url
    }
    return settled
}

private const val ART_SETTLE_MS = 150L

/**
 * The two scrims, drawn once into a cached layer: as gradient shaders, redrawn every frame the
 * backdrop changes, they cost a Fire TV's PowerVR GPU more than the rest of Home together.
 */
@Composable
private fun HeroScrim() {
    Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.05f), 0.45f to Color.Black.copy(alpha = 0.55f), 0.75f to Color.Black.copy(alpha = 0.95f), 1f to Color.Black),
            ),
        )
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.6f).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
    }
}

/** Each size's block at medium text (tvOS `HeroInfo` metrics, halved): height, logo, title, line, overview, lines. */
private data class HeroMetrics(val height: Dp, val logoWidth: Dp, val logoHeight: Dp, val title: Float, val line: Float, val overview: Float, val lines: Int)

private fun metrics(size: HeroSize) = when (size) {
    HeroSize.FULL -> HeroMetrics(170.dp, 320.dp, 75.dp, 32f, 14f, 13f, 3)
    HeroSize.COMPACT -> HeroMetrics(105.dp, 210.dp, 45.dp, 24f, 12f, 11f, 2)
    HeroSize.MINIMAL, HeroSize.OFF -> HeroMetrics(50.dp, 0.dp, 0.dp, 22f, 12f, 0f, 0)
}

/** How tall the hero block is; it has a fixed height so the rows never move with the overview. */
fun heroHeight(size: HeroSize, textScale: Float): Dp = if (size == HeroSize.OFF) 0.dp else metrics(size).height * textScale

/** Title logo (else the name), the card's line and the overview, bottom-aligned at a fixed height. */
@Composable
fun HeroInfo(hero: HeroContent?, size: HeroSize, textScale: Float, modifier: Modifier = Modifier) {
    if (size == HeroSize.OFF) return
    val m = metrics(size)
    Column(modifier.height(m.height * textScale), verticalArrangement = Arrangement.spacedBy((7 * textScale).dp, Alignment.Bottom)) {
        val logo = Artwork.logo(hero?.logoUrl)
        if (logo != null && m.logoHeight > 0.dp) {
            SubcomposeAsyncImage(
                model = logo, contentDescription = hero?.title, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                error = { HeroTitle(hero?.title ?: "", m.title * textScale) },
                modifier = Modifier.height(m.logoHeight * textScale).width(m.logoWidth * textScale),
            )
        } else {
            HeroTitle(hero?.title ?: "", m.title * textScale)
        }
        Text(hero?.subtitle ?: " ", fontSize = (m.line * textScale).sp, fontWeight = FontWeight.Medium, maxLines = 1)
        if (m.lines > 0) {
            Text(
                hero?.overview ?: " ", fontSize = (m.overview * textScale).sp, color = Color.White.copy(alpha = 0.7f), maxLines = m.lines,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = (500 * maxOf(textScale, 1f)).dp),
            )
        }
    }
}

@Composable
private fun HeroTitle(title: String, size: Float) {
    Text(title, fontSize = size.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
