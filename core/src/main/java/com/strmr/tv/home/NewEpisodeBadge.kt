package com.strmr.tv.home

/**
 * The Expo app's "New" badge on a Continue Watching card whose next episode came out in the
 * last 72 hours (`getRecentEpisodeBadge` in features/home/watch-state-cards.ts): yellow with
 * black content once the episode's prequeue is ready, else grey with white.
 */
data class NewEpisodeBadge(
    /** The server has the episode's stream resolved (`prequeueStatus == "ready"`). */
    val ready: Boolean,
) {
    companion object {
        /** `RECENT_NEXT_EPISODE_BADGE_WINDOW_MS`. */
        const val WINDOW_MS = 72L * 3600 * 1000

        /**
         * Null unless the episode's air time (exact UTC, else the air day from local midnight)
         * has passed and lies within the window.
         */
        fun from(
            airDate: String?,
            airDateTimeUTC: String?,
            prequeueStatus: String?,
            now: Long = System.currentTimeMillis(),
        ): NewEpisodeBadge? {
            val released = ReleaseBadge.airTime(airDate, airDateTimeUTC) ?: return null
            if (released > now || now - released > WINDOW_MS) return null
            return NewEpisodeBadge(ready = prequeueStatus == "ready")
        }
    }
}
