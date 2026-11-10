package com.strmr.tv.ui.sports

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.cards.monogram

/**
 * A team's logo, or its monogram on the team colour when there is none (or it fails). Shared
 * by both apps: the court, field and cards draw it. Plain foundation text, white on the
 * colour, so it needs no app theme.
 */
@Composable
fun SportsTeamLogo(team: SportsTeam, size: Dp) {
    val fallback = @Composable {
        Box(Modifier.size(size).clip(CircleShape).background(teamColor(team.color) ?: Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center) {
            BasicText(team.monogram, style = TextStyle(color = Color.White, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold))
        }
    }
    val logo = team.logo
    if (logo == null) fallback()
    else SubcomposeAsyncImage(model = sportsLogoRequest(LocalContext.current, logo), contentDescription = team.name,
                              contentScale = ContentScale.Fit, error = { fallback() }, modifier = Modifier.size(size))
}

/**
 * Team logos decode at one fixed size, the largest any page draws them (the game header's 55 dp),
 * so the request is resolved before layout and a logo already in the memory cache paints in the
 * card's first frame; the hub warms a row's logos with the same request ([prefetchSportsLogos]) so
 * a card scrolling in doesn't pop its logos in after it.
 */
private const val SPORTS_LOGO_PX = 160

fun sportsLogoRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context).data(url).size(SPORTS_LOGO_PX).build()

/** Starts loading [games]' team logos into Coil's caches. */
fun prefetchSportsLogos(context: Context, games: List<SportsGame>) {
    val loader = context.imageLoader
    games.flatMap { listOf(it.awayTeam.logo, it.homeTeam.logo) }.filterNotNull().distinct()
        .forEach { loader.enqueue(sportsLogoRequest(context, it)) }
}

/** "#rrggbb" (or "rrggbb") as an opaque colour; null when it isn't one. */
fun teamColor(hex: String?): Color? {
    val s = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    return s.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}
