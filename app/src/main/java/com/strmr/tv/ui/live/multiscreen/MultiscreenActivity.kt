package com.strmr.tv.ui.live.multiscreen

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.strmr.tv.app.AppLog
import com.strmr.tv.ui.StrmrTheme

/**
 * Several live channels at once, its own activity as the player is: the screen stays on,
 * leaving it (Home, sleep) releases every stream and returns to the page beneath, and the
 * audio focus is held once here for all the tiles (each tile's player leaves it alone). The
 * display mode never follows a tile's frame rate.
 */
class MultiscreenActivity : ComponentActivity() {
    private var model: MultiscreenModel? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestAudioFocus()
        val m = MultiscreenModel(MultiscreenLauncher.take(), finish = { finish() })
        model = m
        setContent { StrmrTheme { MultiscreenScreen(m) } }
    }

    override fun onStop() {
        super.onStop()
        // Home, sleep or another app: let the streams go, as the live player does.
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        model?.dispose()
        model = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
            )
            .setOnAudioFocusChangeListener { change -> AppLog.i("Multiscreen", "audio focus change $change") }
            .build()
        val result = getSystemService(AudioManager::class.java).requestAudioFocus(request)
        AppLog.i("Multiscreen", "audio focus ${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "granted" else "refused ($result)"}")
        focusRequest = request
    }
}
