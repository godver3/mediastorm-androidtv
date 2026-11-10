package com.strmr.tv.player

import androidx.media3.common.PlaybackException
import com.strmr.tv.player.LiveRetunePolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRetunePolicyTest {
    /** A policy tuned at 0 and playing from 2 s, as a channel that opened normally. */
    private fun playing() = LiveRetunePolicy().apply { tuned(0); playing(2_000) }

    /** Retunes at [now] (after any wait the policy asked for) and plays a second later. */
    private fun LiveRetunePolicy.retuneAt(now: Long, plays: Boolean = true) {
        tuned(now)
        if (plays) playing(now + 1_000)
    }

    @Test fun dropAfterTheSpacingRetunesAtOnce() {
        assertEquals(Decision.Retune(1, 0), playing().sourceLost(30_000))
    }

    @Test fun dropSoonerWaitsOutTheSpacing() {
        // A provider that closed the stream 15 s after it was tuned.
        assertEquals(Decision.Retune(1, 5_000), playing().sourceLost(15_000))
    }

    @Test fun spacingCountsFromTheLastTune() {
        val policy = playing()
        assertEquals(Decision.Retune(1, 5_000), policy.sourceLost(15_000))
        policy.retuneAt(20_000)
        assertEquals(Decision.Retune(2, 8_000), policy.sourceLost(32_000))
    }

    @Test fun budgetIsThreeRetunes() {
        val policy = playing()
        assertEquals(Decision.Retune(1, 0), policy.sourceLost(25_000))
        policy.retuneAt(25_000)
        assertEquals(Decision.Retune(2, 0), policy.sourceLost(50_000))
        policy.retuneAt(50_000)
        assertEquals(Decision.Retune(3, 0), policy.sourceLost(75_000))
        policy.retuneAt(75_000)
        assertEquals(Decision.Exhausted, policy.sourceLost(100_000))
        assertEquals(Decision.Exhausted, policy.sourceLost(130_000))
    }

    @Test fun retuneThatNeverPlaysCountsAndWaits() {
        val policy = playing()
        assertEquals(Decision.Retune(1, 0), policy.sourceLost(40_000))
        policy.retuneAt(40_000, plays = false)
        // Refused straight away (a provider still holding the old connection).
        assertEquals(Decision.Retune(2, 19_000), policy.sourceLost(41_000))
        policy.retuneAt(60_000, plays = false)
        assertEquals(Decision.Retune(3, 19_500), policy.sourceLost(60_500))
        policy.retuneAt(80_000, plays = false)
        assertEquals(Decision.Exhausted, policy.sourceLost(80_500))
    }

    @Test fun neverPlayedGivesUpAtOnce() {
        val policy = LiveRetunePolicy().apply { tuned(0) }
        assertEquals(Decision.NeverPlayed, policy.sourceLost(5_000))
        assertEquals(Decision.NeverPlayed, policy.sourceLost(50_000))
    }

    @Test fun aMinuteOfPlayRestoresTheBudget() {
        val policy = playing()
        policy.sourceLost(25_000)
        policy.retuneAt(25_000)
        policy.sourceLost(50_000)
        policy.retuneAt(50_000)
        policy.sourceLost(75_000)
        // The third retune holds for an hour before the next drop.
        policy.retuneAt(75_000)
        assertEquals(Decision.Retune(1, 0), policy.sourceLost(3_675_000))
    }

    @Test fun lessThanAMinuteOfPlayKeepsCounting() {
        val policy = playing()
        policy.sourceLost(25_000)
        policy.retuneAt(25_000)
        policy.sourceLost(50_000)
        policy.retuneAt(50_000)
        policy.sourceLost(75_000)
        policy.retuneAt(75_000)
        // Played from 76 s to 135.9 s: just short of a minute.
        assertEquals(Decision.Exhausted, policy.sourceLost(135_900))
    }

    @Test fun anOpeningOfTheChannelStartsAFreshBudget() {
        val spent = playing()
        repeat(3) { i ->
            spent.sourceLost(25_000L * (i + 1))
            spent.retuneAt(25_000L * (i + 1))
        }
        assertEquals(Decision.Exhausted, spent.sourceLost(100_000))
        assertEquals(Decision.Retune(1, 0), playing().sourceLost(100_000))
    }

    @Test fun sourceErrorsAreNetworkAndStreamFailures() {
        listOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        ).forEach { assertTrue("$it", LiveRetunePolicy.isSourceError(it)) }
        listOf(
            PlaybackException.ERROR_CODE_UNSPECIFIED,
            // Rejoined at the live edge by the player, not tuned again.
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
        ).forEach { assertFalse("$it", LiveRetunePolicy.isSourceError(it)) }
    }
}
