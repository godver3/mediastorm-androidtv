package com.strmr.tv.ui.details

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.strmr.tv.api.Episode
import com.strmr.tv.api.EpisodeRef
import com.strmr.tv.api.Season

/**
 * Spoiler hiding on the details page, as the Expo app's episodePrivacy: unwatched episodes
 * past the one up next get their still and overview blurred (Settings › Spoilers), the
 * up-next one too with Include Next Episode.
 */
object EpisodePrivacy {
    /**
     * The episode blurring counts from: up next (watch progress, not the season on screen),
     * else the first regular episode.
     */
    fun currentEpisode(seasons: List<Season>, upNext: EpisodeRef?): Episode? {
        val episodes = seasons.flatMap { it.episodes }
        upNext?.let { ref -> episodes.firstOrNull { DetailsModel.isUpNext(it, ref) }?.let { return it } }
        return episodes.filter { !isSpecial(it) }.minWithOrNull(compareBy({ it.seasonNumber }, { it.episodeNumber }))
    }

    fun shouldBlur(episode: Episode, current: Episode?, enabled: Boolean, includeCurrent: Boolean, watched: Boolean): Boolean {
        val isCurrent = current != null && episode.seasonNumber == current.seasonNumber && episode.episodeNumber == current.episodeNumber
        return enabled && !watched && (includeCurrent || (!isCurrent && !isAtOrBefore(episode, current)))
    }

    private fun isSpecial(episode: Episode?): Boolean = (episode?.seasonNumber ?: 0) <= 0

    private fun isAtOrBefore(episode: Episode, current: Episode?): Boolean {
        if (current == null || isSpecial(episode)) return false
        if (episode.seasonNumber == current.seasonNumber && episode.episodeNumber == current.episodeNumber) return true
        if (isSpecial(current)) return false
        return episode.seasonNumber < current.seasonNumber ||
            (episode.seasonNumber == current.seasonNumber && episode.episodeNumber < current.episodeNumber)
    }
}

/**
 * A blurred still's image request. Decoded at a few pixels and stretched, which blurs it on
 * every device for nothing per frame (Fire TV has no RenderEffect); [blurredStill] smooths
 * the blocks where RenderEffect exists.
 */
@Composable
fun blurredStillRequest(url: String?): ImageRequest =
    ImageRequest.Builder(LocalContext.current).data(url).size(16, 9).build()

fun Modifier.blurredStill(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blur(10.dp) else this

/**
 * A blurred overview: the text drawn only as its own soft shadow, so it reads as text
 * without being legible, on every API level. Talkback hears that it is hidden.
 */
fun blurredTextStyle(color: Color): TextStyle =
    TextStyle(color = Color.Transparent, shadow = Shadow(color, Offset.Zero, blurRadius = 14f))

fun Modifier.hiddenOverviewSemantics(): Modifier = clearAndSetSemantics { contentDescription = "Overview hidden to avoid spoilers" }
