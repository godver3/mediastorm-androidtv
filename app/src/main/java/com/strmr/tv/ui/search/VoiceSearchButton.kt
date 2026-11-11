package com.strmr.tv.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import com.strmr.tv.ui.AppIcons

/**
 * The microphone beside the Search field: Select opens the system speech prompt (Google's
 * voice search on Google TV / Android TV) and its best match becomes the query. Hidden when
 * nothing on the device answers the speech intent (Fire TV, whose voice goes through Alexa
 * or the keyboard's own mic), so it never offers a button that can't listen.
 */
@Composable
fun VoiceSearchButton(onResult: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val available = remember(context) { canRecognizeSpeech(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull { it.isNotBlank() }
            ?.let { onResult(it.trim()) }
    }
    if (!available) return
    IconButton(
        onClick = { runCatching { launcher.launch(speechIntent(context)) }.onFailure { if (it !is ActivityNotFoundException) throw it } },
        modifier = modifier,
    ) {
        Icon(AppIcons.Mic, contentDescription = "Search by voice")
    }
}

/** Google TV's voice search (Katniss), the remote-mic prompt TV users know. */
private const val TV_VOICE_PACKAGE = "com.google.android.katniss"

/**
 * The speech prompt, sent straight to the TV voice search when it's installed: a device can
 * also carry Google's generic recognizer, and two handlers mean an Android chooser first.
 */
private fun speechIntent(context: Context) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Search movies, shows and people")
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    val tvVoice = Intent(action).setPackage(TV_VOICE_PACKAGE)
    if (context.packageManager.queryIntentActivities(tvVoice, 0).isNotEmpty()) setPackage(TV_VOICE_PACKAGE)
}

/** Needs the manifest's `<queries>` entry for the speech action to see the recognizer on Android 11+. */
private fun canRecognizeSpeech(context: Context): Boolean =
    context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
