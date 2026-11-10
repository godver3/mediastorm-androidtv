package com.strmr.mobile.ui.sports

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The sports pages' side margin on a phone (the TV's PageMargin). */
val PageMargin = 16.dp

/** Secondary text: the theme's muted ink (the TV's 60 % white). */
val SportsMuted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** A step fainter than [SportsMuted] (tvOS `.tertiary`). */
val SportsFaint: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

/** The status colours every sports app shares: live red, schedule-change amber. */
val SportsLive = Color(0xFFFF334D)
val SportsAmber = Color(0xFFE6B75A)

/** The sports accent (#8ebcf7, Expo's Midnight sports theme): the score-change cue, hockey goal badges. */
val SportsAccent = Color(0xFF8EBCF7)

/** A hub card's surface. */
val SportsCardColor: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh

/**
 * True on a phone-width page: multi-column layouts ([com.strmr.mobile.ui.sports.game.SportsColumns])
 * stack their columns. Set by each sports page from its width.
 */
val LocalSportsCompact = compositionLocalOf { true }

/**
 * A Material filter chip taking the TV chip's content lambda, so the ported sports panels keep
 * their chip rows as they are.
 */
@Composable
fun SportsChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FilterChip(
        selected = selected, onClick = onClick, modifier = modifier, enabled = enabled, leadingIcon = leadingIcon,
        label = { Row(verticalAlignment = Alignment.CenterVertically, content = content) },
    )
}
