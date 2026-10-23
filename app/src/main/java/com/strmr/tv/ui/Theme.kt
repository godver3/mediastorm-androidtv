package com.strmr.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import kotlin.math.ceil

/** Backdrop of the sign-in screens; matches the bundled logo's own background. */
val BrandBackground = Color(0xFF0D0F12)

/** The brand accent, for progress bars and selected states (the tvOS app's tint). */
val Accent = Color(0xFFF5B400)

/** Side margin of Home and details (tvOS: 80 pt padding inside its 80 pt safe area). */
val ContentMargin = 80.dp

/** Side margin of the other pages: grids, Live, Search, Settings (as tvOS lays them out). */
val PageMargin = 64.dp

/** How much an artwork card grows when focused, as on tvOS (`PosterFocusStyle`). */
const val CardFocusScale = 1.06f

/**
 * An artwork card's focus ring: a thin white line inside its edge (tvOS `PosterFocusStyle`),
 * in place of Compose for TV's grey 3 dp border; with the [CardFocusScale] lift it shows
 * focus on dark cards over a black page too.
 */
@Composable
fun cardFocusBorder(): CardBorder = CardDefaults.border(focusedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))))

/**
 * The gap from an artwork card to the caption under it: how far focus scaling pushes the
 * card's bottom edge down, plus 6 dp that stay clear while it is focused (tvOS 12 pt).
 */
fun captionGap(cardHeight: Dp): Dp = (ceil(cardHeight.value * (CardFocusScale - 1f) / 2f) + 6f).dp

/** Text with SF's tight tracking and leading: Material's TV type scale spaces letters and lines wider. */
private fun TextStyle.tight() = copy(letterSpacing = 0.sp, lineHeight = 1.25.em)

private val TightTypography = Typography().run {
    copy(
        displayLarge = displayLarge.tight(), displayMedium = displayMedium.tight(), displaySmall = displaySmall.tight(),
        headlineLarge = headlineLarge.tight(), headlineMedium = headlineMedium.tight(), headlineSmall = headlineSmall.tight(),
        titleLarge = titleLarge.tight(), titleMedium = titleMedium.tight(), titleSmall = titleSmall.tight(),
        bodyLarge = bodyLarge.tight(), bodyMedium = bodyMedium.tight(), bodySmall = bodySmall.tight(),
    )
}

/** Compose for TV's own dark scheme with the brand accent; components keep their native look. */
@Composable
fun StrmrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = BrandBackground,
            surface = BrandBackground,
        ),
        typography = TightTypography,
    ) {
        // Text outside a Surface reads these; the TV default is black, in a wide-set body style.
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalTextStyle provides TightTypography.bodyLarge,
            content = content,
        )
    }
}
