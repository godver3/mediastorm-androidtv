package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strmr.tv.api.SourceAvailability
import com.strmr.tv.home.SourceHint
import com.strmr.tv.home.sourceHintFlow

/** Visible cards age locally; no network request or background source work is started. */
@Composable
fun rememberSourceHint(source: SourceAvailability?): SourceHint? {
    if (source == null) return null
    val hint = remember(source) { mutableStateOf(SourceHint.from(source)) }
    LaunchedEffect(source) { sourceHintFlow(source).collect { hint.value = it } }
    return hint.value
}

/** Apply grey and dimming to artwork alone, preserving text, badges and focus contrast. */
fun Modifier.sourceHintArtwork(hint: SourceHint?): Modifier = if (hint?.dimArtwork != true) this else drawWithCache {
    val paint = Paint().apply {
        alpha = 0.55f
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    onDrawWithContent {
        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
        drawContent()
        drawContext.canvas.restore()
    }
}

/** Source status is independent of optional watch/release badges and never consumes clicks. */
@Composable
fun SourceHintBadge(hint: SourceHint, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .clearAndSetSemantics { contentDescription = hint.description() }.padding(horizontal = 5.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        BasicText(hint.label, style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
        hint.checkedLabel()?.let { checked ->
            BasicText(checked, style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
