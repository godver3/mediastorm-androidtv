package com.strmr.tv.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.home.HomeCard

/**
 * An expanded poster's info (Settings › Home Layout › Show Info When Expanded): the title's
 * logo (else its name), its year / rating / genre line and overview over the bottom of the
 * 16:9 art, on a gradient dark enough to read on bright art, clear of the progress line.
 */
@Composable
fun ExpandedCardInfo(card: HomeCard, cardHeight: Dp, progress: Boolean, modifier: Modifier = Modifier) {
    // Type scale for the card size (1 at the Large 135 dp card).
    val scale = maxOf(cardHeight.value / CardMetrics.BASE_HEIGHT, 0.6f)
    val logoW = cardHeight * (16f / 9f) * 0.45f
    val logoH = cardHeight * 0.24f
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.9f)))
            .padding(start = (10 * scale).dp, end = (10 * scale).dp, top = (24 * scale).dp, bottom = ((if (progress) 15 else 8) * scale).dp),
        verticalArrangement = Arrangement.spacedBy((3 * scale).dp),
    ) {
        val logo = Artwork.logo(card.logoUrl)
        if (logo != null) {
            SubcomposeAsyncImage(model = logo, contentDescription = card.title) {
                when (val s = painter.state) {
                    is AsyncImagePainter.State.Success -> {
                        val px = s.painter.intrinsicSize
                        if (px.isSpecified && px.width > 0f && px.height > 0f) {
                            val fit = minOf(logoW.value / px.width, logoH.value / px.height)
                            Image(painter, card.title, contentScale = ContentScale.FillBounds,
                                  modifier = Modifier.size((px.width * fit).dp, (px.height * fit).dp))
                        } else {
                            Name(card.title, scale)
                        }
                    }
                    is AsyncImagePainter.State.Error -> Name(card.title, scale)
                    else -> Spacer(Modifier.height(logoH))
                }
            }
        } else {
            Name(card.title, scale)
        }
        metaLine(card).takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = (9.5f * scale).sp, lineHeight = (12 * scale).sp, fontWeight = FontWeight.SemiBold,
                 color = Color.White.copy(alpha = 0.9f), maxLines = if (card.forceTitleOverlay) 3 else 1, overflow = TextOverflow.Ellipsis)
        }
        card.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = (8.5f * scale).sp, lineHeight = (11 * scale).sp, color = Color.White.copy(alpha = 0.8f),
                 maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Name(title: String, scale: Float) {
    Text(title, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
