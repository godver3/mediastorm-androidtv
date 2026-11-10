package com.strmr.mobile.player

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPrefsTest {
    @Test
    fun phonesFollowTheChoiceAndTabletsTurnFreely() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, playerOrientation(false, PlayerPrefs.Orientation.LANDSCAPE))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, playerOrientation(false, PlayerPrefs.Orientation.PORTRAIT))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, playerOrientation(false, PlayerPrefs.Orientation.DEVICE))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, playerOrientation(true, PlayerPrefs.Orientation.LANDSCAPE))
    }
}
