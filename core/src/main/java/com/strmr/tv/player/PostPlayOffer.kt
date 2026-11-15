package com.strmr.tv.player

import com.strmr.tv.library.ProfileDataContext

/** One regular end, never a trailer/live/error or a normal episode with a next target. */
class PostPlayOffer(private val context: Long = ProfileDataContext.current) {
    private var offered = false
    fun offer(request: PlaybackRequest, reportsProgress: Boolean, live: Boolean, discarded: Boolean,
              positionMs: Long, durationMs: Long, failed: Boolean): Boolean {
        if (offered || context != ProfileDataContext.current || !reportsProgress || live || discarded || request.nextEpisode != null || (request.isEpisode && !request.episodeOrderKnown) ||
            !EpisodeAdvancePolicy.genuineEnd(positionMs, durationMs, failed)) return false
        offered = true
        return true
    }
}
