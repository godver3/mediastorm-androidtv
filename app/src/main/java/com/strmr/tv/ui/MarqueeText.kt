package com.strmr.tv.ui

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * One line of text that ellipsizes at rest and, while [active] (its row is focused), scrolls
 * horizontally to reveal the rest when it is longer than the row. Text that fits stays still.
 * Used for release / file names in the source picker and the library versions list.
 */
@Composable
fun MarqueeText(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    if (active) {
        Text(
            text,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            modifier = modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                animationMode = MarqueeAnimationMode.Immediately,
                repeatDelayMillis = 1_500,
                initialDelayMillis = 1_000,
                spacing = MarqueeSpacing(48.dp),
                velocity = 40.dp,
            ),
        )
    } else {
        Text(text, color = color, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
    }
}
