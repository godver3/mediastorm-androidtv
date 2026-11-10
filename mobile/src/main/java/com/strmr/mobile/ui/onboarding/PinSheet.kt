package com.strmr.mobile.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.strmr.tv.api.Profile
import com.strmr.tv.app.PinVerification
import kotlinx.coroutines.launch

private const val MIN_LENGTH = 4
private const val MAX_LENGTH = 8

/**
 * A profile's PIN on a bottom sheet with a number pad. A known PIN length submits on the last
 * digit; otherwise OK submits. The presenter dismisses it on [PinVerification.Unlocked].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSheet(profile: Profile, verify: suspend (String) -> PinVerification, onCancel: () -> Unit) {
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val fixedLength = profile.pinLength?.takeIf { it >= MIN_LENGTH }?.coerceAtMost(MAX_LENGTH)
    val dots = fixedLength ?: maxOf(MIN_LENGTH, digits.length)

    fun submit() {
        if (verifying || digits.length < MIN_LENGTH) return
        val entered = digits
        verifying = true
        scope.launch {
            val result = verify(entered)
            verifying = false
            when (result) {
                PinVerification.Unlocked -> Unit
                PinVerification.Wrong -> {
                    digits = ""
                    message = "Incorrect PIN. Try again."
                }
                is PinVerification.Failed -> message = result.message
            }
            if (result != PinVerification.Unlocked) {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                shake.animateTo(0f, keyframes { durationMillis = 400; 14f at 70; -14f at 140; 10f at 210; -6f at 280; 3f at 340 })
            }
        }
    }

    fun press(digit: Int) {
        if (verifying || digits.length >= (fixedLength ?: MAX_LENGTH)) return
        digits += digit
        message = null
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (fixedLength != null && digits.length == fixedLength) submit()
    }

    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileAvatar(profile, 64.dp)
            Text("Enter the PIN for ${profile.name}", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                repeat(dots) { i ->
                    val filled = i < digits.length
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .then(if (filled) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
            }
            Text(
                message ?: " ", style = MaterialTheme.typography.bodyMedium,
                color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberPad(
                enabled = !verifying,
                onDigit = ::press,
                onDelete = { if (digits.isNotEmpty()) digits = digits.dropLast(1) },
                onSubmit = if (fixedLength == null) ({ submit() }) else null,
                canSubmit = digits.length >= MIN_LENGTH,
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun NumberPad(enabled: Boolean, onDigit: (Int) -> Unit, onDelete: () -> Unit, onSubmit: (() -> Unit)?, canSubmit: Boolean) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { d -> PadKey(enabled, { onDigit(d) }) { Text("$d", style = MaterialTheme.typography.headlineSmall) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (onSubmit != null) {
                PadKey(enabled && canSubmit, onSubmit) { Text("OK", style = MaterialTheme.typography.titleMedium) }
            } else {
                Box(Modifier.size(72.dp))
            }
            PadKey(enabled, { onDigit(0) }) { Text("0", style = MaterialTheme.typography.headlineSmall) }
            PadKey(enabled, onDelete) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete") }
        }
    }
}

@Composable
private fun PadKey(enabled: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilledTonalButton(onClick, Modifier.size(72.dp), enabled = enabled, shape = CircleShape) { label() }
}
