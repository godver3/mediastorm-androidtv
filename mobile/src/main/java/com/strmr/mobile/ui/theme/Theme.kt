package com.strmr.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The brand accent (the TV apps' tint), for primary actions, progress and selection. */
val Accent = Color(0xFFF5B400)
val BrandBackground = Color(0xFF0D0F12)

/**
 * Material 3's dark scheme generated from the amber accent, over the TV apps' near-black.
 * Fixed (no wallpaper colours), so the app looks the same on every phone.
 */
private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF3F2E00),
    primaryContainer = Color(0xFF5B4300),
    onPrimaryContainer = Color(0xFFFFDF9E),
    inversePrimary = Color(0xFF785A00),
    secondary = Color(0xFFD9C4A0),
    onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF3D3420),
    onSecondaryContainer = Color(0xFFF2E1BC),
    tertiary = Color(0xFFB2CFA8),
    onTertiary = Color(0xFF1E361A),
    tertiaryContainer = Color(0xFF344D2F),
    onTertiaryContainer = Color(0xFFCDEBC2),
    background = BrandBackground,
    onBackground = Color(0xFFE9E2D4),
    surface = BrandBackground,
    onSurface = Color(0xFFE9E2D4),
    surfaceVariant = Color(0xFF2A2721),
    onSurfaceVariant = Color(0xFFCFC6B2),
    surfaceTint = Accent,
    inverseSurface = Color(0xFFE9E2D4),
    inverseOnSurface = Color(0xFF34302A),
    outline = Color(0xFF989080),
    outlineVariant = Color(0xFF4D4637),
    surfaceDim = BrandBackground,
    surfaceBright = Color(0xFF34322E),
    surfaceContainerLowest = Color(0xFF08090B),
    surfaceContainerLow = Color(0xFF15171A),
    surfaceContainer = Color(0xFF1A1C20),
    surfaceContainerHigh = Color(0xFF24262A),
    surfaceContainerHighest = Color(0xFF2F3135),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun MediaStormTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
