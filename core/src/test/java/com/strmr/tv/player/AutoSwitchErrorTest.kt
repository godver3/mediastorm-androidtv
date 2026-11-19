package com.strmr.tv.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which player errors send Auto Switch to the next source instead of the error screen. */
class AutoSwitchErrorTest {
    @Test fun sourceReadAndContainerErrorsSwitch() {
        assertTrue(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertTrue(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertTrue(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertTrue(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }

    @Test fun deviceErrorsShowTheErrorScreen() {
        assertFalse(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        assertFalse(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_DRM_UNSPECIFIED))
        assertFalse(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertFalse(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW))
        assertFalse(AutoSwitch.switchesOnError(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }
}
