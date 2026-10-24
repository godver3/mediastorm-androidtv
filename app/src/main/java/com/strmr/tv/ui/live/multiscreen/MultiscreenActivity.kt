package com.strmr.tv.ui.live.multiscreen

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
        val (handover, resume) = MultiscreenLauncher.take()
        val m = MultiscreenModel(handover, resume, finish = { finish() })
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
        val audio = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(focusListener)
        }
        focusRequest = null
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change -> AppLog.i("Multiscreen", "audio focus change $change") }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Android 7 (Fire OS 6) has no AudioFocusRequest.
            @Suppress("DEPRECATION")
            val result = getSystemService(AudioManager::class.java)
                .requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            AppLog.i("Multiscreen", "audio focus ${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "granted" else "refused ($result)"}")
            return
        }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        val result = getSystemService(AudioManager::class.java).requestAudioFocus(request)
        AppLog.i("Multiscreen", "audio focus ${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "granted" else "refused ($result)"}")
        focusRequest = request
    }
}
