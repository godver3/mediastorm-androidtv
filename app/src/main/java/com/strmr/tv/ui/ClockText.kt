package com.strmr.tv.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.ui.settings.ClockSettings
import java.util.Date

/**
 * The current time on its own glass pill (the player's chrome, the side-menu style's Home), or
 * bare (`pill = false`) at the end of the top tab bar's pill, in the device's 12 / 24-hour format. Not focusable. It changes on the system's minute tick (also
 * delivered on waking from sleep) and on a time, zone, locale or 12 / 24-hour change, never more often.
 * Hidden by Settings › Appearance › Show Clock.
 */
@Composable
internal fun ClockText(modifier: Modifier = Modifier, pill: Boolean = true) {
    if (!ClockSettings.enabled) return
    val context = LocalContext.current
    var time by remember { mutableStateOf(formatNow(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { time = formatNow(context) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // Registered after the first format: a minute may have turned in between.
        time = formatNow(context)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    Text(
        time,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.85f),
        modifier = if (pill) {
            modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 7.dp)
        } else {
            modifier
        },
    )
}

/** The device's short time format (`DateFormat.getTimeFormat`, which follows its 12 / 24-hour setting). */
private fun formatNow(context: Context): String = DateFormat.getTimeFormat(context).format(Date())
