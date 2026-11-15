package com.strmr.tv.api

import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class EpisodeAutoplaySettingsTest {
    @Test fun `decoded explicit false wins over a device override and credits on`() {
        val user = ApiJson.decodeFromString<UserPlayback>("""{"playback":{"nextEpisodeAutoPlay":false,"creditsAutoSkip":true}}""")
        val device = ApiJson.decodeFromString<PlaybackFields>("""{"nextEpisodeAutoPlay":true,"creditsAutoSkip":true}""")
        val settings = mergePlaybackSettings(user.playback, device)
        assertFalse(settings.nextEpisodeAutoPlay); assertTrue(settings.creditsAutoSkip)
    }
    @Test fun `legacy profile keeps end autoplay without enabling credits skipping`() {
        val user = ApiJson.decodeFromString<UserPlayback>("""{"playback":{"preferredAudioLanguage":"fra"}}""")
        val settings = mergePlaybackSettings(user.playback, null)
        assertTrue(settings.nextEpisodeAutoPlay); assertFalse(settings.creditsAutoSkip); assertEquals("fra",settings.preferredAudioLanguage)
    }
    @Test fun `unavailable profile does not take a device autoplay default`() {
        assertFalse(mergePlaybackSettings(null, PlaybackFields(nextEpisodeAutoPlay = true)).nextEpisodeAutoPlay)
        assertFalse(mergePlaybackSettings(null, null).nextEpisodeAutoPlay)
    }
    @Test fun `profile autoplay remains on when the device disables credits`() {
        val settings = mergePlaybackSettings(PlaybackFields(nextEpisodeAutoPlay = true, creditsAutoSkip = true), PlaybackFields(nextEpisodeAutoPlay = false, creditsAutoSkip = false))
        assertTrue(settings.nextEpisodeAutoPlay); assertFalse(settings.creditsAutoSkip)
    }
}
