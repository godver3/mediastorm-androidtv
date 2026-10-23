package com.strmr.tv.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Profile
import com.strmr.tv.api.iconUrl

/**
 * Round profile image: the server icon when there is one, otherwise the initial on the
 * profile colour (or a stable colour derived from the ID).
 */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    val base = profile.color?.let(::parseHex) ?: palette[profile.id.sumOf { it.code } % palette.size]
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(base, base.copy(alpha = 0.75f)))),
        contentAlignment = Alignment.Center,
    ) {
        val icon = ApiClient.iconUrl(profile)
        if (icon != null) {
            // The initial shows only if the icon fails, never while it loads.
            SubcomposeAsyncImage(
                model = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                error = { Initial(profile, size) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Initial(profile, size)
        }
    }
}

@Composable
private fun Initial(profile: Profile, size: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            profile.name.firstOrNull()?.uppercase() ?: "?",
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

private val palette = listOf(
    Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF375F), Color(0xFFFF9F0A),
    Color(0xFF64D2FF), Color(0xFF5E5CE6), Color(0xFF30D158), Color(0xFFFF453A),
)

/** `#RRGGBB` or `RRGGBB`. */
fun parseHex(hex: String): Color? {
    val s = hex.trim().removePrefix("#")
    if (s.length != 6) return null
    val v = s.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}
