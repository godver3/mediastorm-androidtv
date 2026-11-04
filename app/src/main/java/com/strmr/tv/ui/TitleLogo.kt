package com.strmr.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.ui.settings.LogoSettings

/**
 * A title's logo fitted (bottom-start) in [width] x [height] at [place]'s Title Logo Size
 * ([LogoSettings]); holds that slot while loading. If the logo fails, [fallback] (the name)
 * is drawn at its own size, not squeezed into a scaled-down slot.
 */
@Composable
fun TitleLogo(
    url: String, title: String, width: Dp, height: Dp, modifier: Modifier = Modifier,
    place: LogoSettings.Place = LogoSettings.Place.HOME, fallback: @Composable () -> Unit,
) {
    val factor = LogoSettings.scale(place).factor
    val slot = Modifier.size(width * factor, height * factor)
    SubcomposeAsyncImage(model = url, contentDescription = title, modifier = modifier) {
        when (painter.state) {
            is AsyncImagePainter.State.Success ->
                Image(painter, title, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, modifier = slot)
            is AsyncImagePainter.State.Error -> fallback()
            else -> Spacer(slot)
        }
    }
}
