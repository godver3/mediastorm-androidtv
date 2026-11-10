package com.strmr.tv.ui.sports

import com.strmr.tv.sports.SportsCyclingListing
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsRaceListing
import java.time.Instant

/**
 * One card of the Sports hub (Expo `HubCard`): a game, a race weekend's session, or a cycling
 * race's stage (or rest day). Rows sort them together by [lifecycle] and [start].
 */
sealed interface SportsHubItem {
    /** Unique across kinds: the row's item key. */
    val key: String
    val league: String
    val lifecycle: SportsLifecycle
    val start: Instant

    data class Match(val game: SportsGame) : SportsHubItem {
        override val key: String get() = game.id
        override val league: String get() = game.league
        override val lifecycle: SportsLifecycle get() = game.lifecycle
        override val start: Instant get() = game.start
    }

    data class Race(val listing: SportsRaceListing) : SportsHubItem {
        override val key: String get() = listing.key
        override val league: String get() = listing.session.league
        override val lifecycle: SportsLifecycle get() = listing.lifecycle
        override val start: Instant get() = listing.session.start ?: Instant.EPOCH
    }

    data class Cycling(val listing: SportsCyclingListing) : SportsHubItem {
        override val key: String get() = listing.key
        override val league: String get() = listing.race.followId
        override val lifecycle: SportsLifecycle get() = listing.lifecycle
        override val start: Instant get() = listing.start
    }
}
