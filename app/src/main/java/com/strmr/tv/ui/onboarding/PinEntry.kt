package com.strmr.tv.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.strmr.tv.api.Profile
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.PinVerification
import com.strmr.tv.ui.BrandBackground
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val MIN_LENGTH = 4
private const val MAX_LENGTH = 16

/**
 * Full-screen PIN entry for a protected profile: header and PIN dots beside a focusable
 * number pad. Fixed-length PINs (the server reports `pinLength`) submit on the last digit;
 * otherwise OK submits once the minimum length is reached. A wrong PIN shakes the dots and
 * clears them. Back (or Cancel) backs out. A full-screen dialog: its own window keeps focus
 * and Back off the screen underneath.
 */
@Composable
fun PinEntry(profile: Profile, notice: String? = null, verify: suspend (String) -> PinVerification, onCancel: () -> Unit) {
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf(notice) }
    var isError by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val firstKey = remember { FocusRequester() }
    val fixedLength = profile.pinLength?.takeIf { it >= MIN_LENGTH }?.coerceAtMost(MAX_LENGTH)
    val dotCount = fixedLength ?: maxOf(MIN_LENGTH, digits.length)

    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    fun submit() {
        if (verifying || digits.length < MIN_LENGTH) return
        val entered = digits
        verifying = true
        scope.launch {
            val result = verify(entered)
            verifying = false
            when (result) {
                PinVerification.Unlocked -> Unit // the presenter dismisses
                PinVerification.Wrong -> {
                    digits = ""
                    message = "Incorrect PIN. Try again."
                    isError = true
                    runCatching { firstKey.requestFocus() }
                }
                is PinVerification.Failed -> {
                    message = result.message
                    isError = true
                }
            }
            if (result != PinVerification.Unlocked) {
                shake.animateTo(0f, keyframes { durationMillis = 400; 12f at 70; -12f at 140; 9f at 210; -6f at 280; 3f at 340 })
            }
        }
    }

    fun press(key: Int) {
        if (verifying) return
        if (key < 0) {
            digits = digits.dropLast(1)
            return
        }
        if (digits.length >= (fixedLength ?: MAX_LENGTH)) return
        if (digits.isEmpty()) message = null
        digits += key.toString()
        if (fixedLength != null && digits.length == fixedLength) submit()
    }

    // A remote's number keys type straight in, as on the pad.
    val numberKeys = Modifier.onPreviewKeyEvent { event ->
        val digit = digitKeys.indexOf(event.key)
        val handled = digit >= 0 || event.key == Key.Delete || event.key == Key.Backspace
        if (handled && event.type == KeyEventType.KeyDown) press(if (digit >= 0) digit else -1)
        handled
    }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
    Box(
        Modifier.fillMaxSize().background(BrandBackground).then(numberKeys),
        contentAlignment = Alignment.Center,
    ) {
        // Side by side: a 4-row pad doesn't fit under the header on a 540 dp screen.
        Row(horizontalArrangement = Arrangement.spacedBy(70.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(320.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileAvatar(profile, 90.dp)
                Spacer(Modifier.height(18.dp))
                Text("Enter PIN for ${profile.name}", style = MaterialTheme.typography.titleLarge,
                     fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Box(contentAlignment = Alignment.Center) {
                    Row(Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(dotCount) { i ->
                            Box(
                                Modifier.size(17.dp).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                    .background(if (i < digits.length) Color.White else Color.Transparent, CircleShape),
                            )
                        }
                    }
                    // Beside the dots, not in their row, so the dots never shift while verifying.
                    if (verifying) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp).offset { IntOffset((dotCount * 31 / 2 + 24).dp.roundToPx(), 0) },
                            color = Color.White, strokeWidth = 2.dp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    message ?: " ",
                    color = if (isError) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(40.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    if (fixedLength == null) {
                        OutlinedButton(onClick = ::submit, enabled = digits.length >= MIN_LENGTH && !verifying) { Text("OK") }
                    }
                }
            }
            NumberPad(firstKey, enabled = !verifying, onPress = ::press)
        }
    }
    }
}

/** 1–9, then 0 and delete under them, as round keys that lift on focus. */
@Composable
private fun NumberPad(firstKey: FocusRequester, enabled: Boolean, onPress: (Int) -> Unit) {
    Column(Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                for (col in 1..3) {
                    val n = row * 3 + col
                    PadKey(enabled, { onPress(n) }, if (n == 1) Modifier.focusRequester(firstKey) else Modifier) {
                        Text(n.toString(), fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            Spacer(Modifier.size(KEY_SIZE))
            PadKey(enabled, { onPress(0) }) { Text("0", fontSize = 26.sp, fontWeight = FontWeight.Medium) }
            PadKey(enabled, { onPress(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Delete") }
        }
    }
}

private val KEY_SIZE = 60.dp

private val digitKeys = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

@Composable
private fun PadKey(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        modifier = modifier.size(KEY_SIZE),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { label() }
    }
}

/** Asks for a PIN when a request hits a locked profile mid-session, over whatever screen is showing. */
@Composable
fun ProfileLockPrompt(challenge: AppSession.PinChallenge) {
    PinEntry(
        profile = challenge.profile,
        notice = "This profile is locked. Enter its PIN to continue.",
        verify = { AppSession.verifyChallenge(it) },
        onCancel = { AppSession.cancelChallenge() },
    )
}
