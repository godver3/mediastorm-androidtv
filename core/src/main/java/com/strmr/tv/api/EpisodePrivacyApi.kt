package com.strmr.tv.api

import com.strmr.tv.app.Config
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

/**
 * The profile's spoiler settings (`display.blurUnwatchedEpisode*`), layered as the Expo app
 * layers them: this device's override from `/api/clients/{id}/settings`, then the profile's
 * (the server's defaults already filled in), else off.
 */
data class EpisodePrivacySettings(
    /** Unwatched episodes' stills are blurred. */
    val blurThumbnails: Boolean = false,
    /** …the up-next episode's too. */
    val blurThumbnailsIncludeCurrent: Boolean = false,
    /** Unwatched episodes' overviews are blurred. */
    val blurOverviews: Boolean = false,
    /** …the up-next episode's too. */
    val blurOverviewsIncludeCurrent: Boolean = false,
)

@Serializable
private data class PrivacyFields(
    val blurUnwatchedEpisodeThumbnails: Boolean? = null,
    val blurUnwatchedEpisodeThumbnailsIncludeCurrent: Boolean? = null,
    val blurUnwatchedEpisodeOverviews: Boolean? = null,
    val blurUnwatchedEpisodeOverviewsIncludeCurrent: Boolean? = null,
)

@Serializable
private data class UserPrivacy(val display: PrivacyFields? = null)

suspend fun ApiClient.episodePrivacy(): EpisodePrivacySettings = coroutineScope {
    val user = async {
        runCatching { send<UserPrivacy>("GET", "$profilePath/settings", promptOnLock = false, timeoutMs = 10_000) }.getOrNull()?.display
    }
    val device = async {
        runCatching {
            send<PrivacyFields>("GET", "/api/clients/${segment(Config.clientId)}/settings", mapOf("userId" to profileId),
                                promptOnLock = false, timeoutMs = 10_000)
        }.getOrNull()
    }
    val (u, d) = user.await() to device.await()
    EpisodePrivacySettings(
        blurThumbnails = d?.blurUnwatchedEpisodeThumbnails ?: u?.blurUnwatchedEpisodeThumbnails ?: false,
        blurThumbnailsIncludeCurrent = d?.blurUnwatchedEpisodeThumbnailsIncludeCurrent ?: u?.blurUnwatchedEpisodeThumbnailsIncludeCurrent ?: false,
        blurOverviews = d?.blurUnwatchedEpisodeOverviews ?: u?.blurUnwatchedEpisodeOverviews ?: false,
        blurOverviewsIncludeCurrent = d?.blurUnwatchedEpisodeOverviewsIncludeCurrent ?: u?.blurUnwatchedEpisodeOverviewsIncludeCurrent ?: false,
    )
}
