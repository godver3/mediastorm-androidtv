package com.strmr.tv.player

import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text

/**
 * The player's subtitles, drawn as the tvOS overlay draws them: text cues as one centred block
 * in the device's [SubtitleStyle] (its box rounded and padded around the whole block), bitmap
 * cues (PGS / DVB / DVD) where the stream places them, scaled by the style's size. Sizes are
 * tvOS points of a 1080-point-high video frame, which this fits into its bounds at `videoAspect`.
 */
@Composable
fun SubtitleOverlay(cues: List<Cue>, style: SubtitleStyle, videoAspect: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = if (videoAspect > 0) videoAspect else maxWidth / maxHeight
        val width = minOf(maxWidth, maxHeight * aspect)
        val height = width / aspect
        Box(Modifier.size(width, height)) {
            val pt = height / 1080f
            cues.forEach { cue -> if (cue.bitmap != null) BitmapCue(cue, style.size.toFloat(), width, height) }
            val text = cues.filter { it.bitmap == null }.mapNotNull { it.text?.toString()?.trim() }.filter { it.isNotEmpty() }
            if (text.isNotEmpty()) {
                SubtitleText(
                    text.joinToString("\n"), style, pt,
                    Modifier.align(Alignment.BottomCenter).padding(start = pt * 160, end = pt * 160, bottom = pt * style.bottomMargin.toFloat()),
                )
            }
        }
    }
}

/** One block of text: the box, the outline (a stroke under the fill) or the drop shadow. */
@Composable
private fun SubtitleText(text: String, style: SubtitleStyle, pt: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val fontPx = with(density) { (pt * SubtitleStyle.BASE_POINTS * style.size.toFloat()).toPx() }
    val shadow = style.edge == SubtitleStyle.Edge.SHADOW
    val base = TextStyle(
        color = Color(SubtitleStyle.argb(style.textColor, style.textOpacity)),
        fontSize = with(density) { fontPx.toSp() },
        fontWeight = if (style.bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        shadow = if (shadow) {
            Shadow(Color.Black, Offset.Zero, with(density) { (pt * 4 * maxOf(style.size, 0.75).toFloat()).toPx() })
        } else null,
    )
    val box = if (style.backgroundOpacity > 0) Color(SubtitleStyle.argb(style.backgroundColor, style.backgroundOpacity)) else Color.Transparent
    Box(
        modifier.background(box, RoundedCornerShape(pt * 10))
            .padding(start = pt * 22, end = pt * 22, top = pt * 6, bottom = pt * 8),
    ) {
        if (style.edge == SubtitleStyle.Edge.OUTLINE) {
            // The tvOS stroke width: (2 + 8 × weight)% of the font size.
            val stroke = Stroke(width = fontPx * (2 + 8 * style.outlineWeight.toFloat()) / 100, join = StrokeJoin.Round)
            Text(text, style = base.copy(color = Color(SubtitleStyle.argb(style.outlineColor, style.textOpacity)), drawStyle = stroke))
        }
        Text(text, style = base)
    }
}

/** A bitmap cue at its place in the frame, scaled by `scale` about its bottom centre and kept on screen. */
@OptIn(UnstableApi::class)
@Composable
private fun BitmapCue(cue: Cue, scale: Float, frameWidth: Dp, frameHeight: Dp) {
    val bitmap = cue.bitmap ?: return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val w = if (cue.size != Cue.DIMEN_UNSET) cue.size else 0.8f
    val h = if (cue.bitmapHeight != Cue.DIMEN_UNSET) cue.bitmapHeight
    else w * bitmap.height / bitmap.width * (frameWidth / frameHeight)
    val x = if (cue.position != Cue.DIMEN_UNSET) cue.position - anchor(cue.positionAnchor) * w else (1 - w) / 2
    val y = if (cue.line != Cue.DIMEN_UNSET && cue.lineType == Cue.LINE_TYPE_FRACTION) cue.line - anchor(cue.lineAnchor) * h else 0.9f - h
    val sw = w * scale
    val sh = h * scale
    val left = (x + w / 2 - sw / 2).coerceIn(0f, maxOf(1 - sw, 0f))
    val top = (y + h - sh).coerceIn(0f, maxOf(1 - sh, 0f))
    Image(
        image, contentDescription = null, contentScale = ContentScale.FillBounds,
        modifier = Modifier.offset(frameWidth * left, frameHeight * top).size(frameWidth * sw, frameHeight * sh),
    )
}

private fun anchor(type: Int) = when (type) {
    Cue.ANCHOR_TYPE_MIDDLE -> 0.5f
    Cue.ANCHOR_TYPE_END -> 1f
    else -> 0f
}
