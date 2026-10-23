package com.strmr.tv.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.R
import com.strmr.tv.app.AppSession
import com.strmr.tv.ui.Accent

/**
 * Shared frame for the first-run screens: the brand logo, title, subtitle, then content,
 * centred in a fixed-width column that scrolls when the IME squeezes it.
 */
@Composable
fun OnboardingLayout(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BrandMark(height = 90.dp)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
            content()
        }
    }
}

/** Inline error under a form. */
@Composable
fun OnboardingError(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
        Text(message, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center)
    }
}

/** Primary action that swaps its label for a spinner while busy. */
@Composable
fun BusyButton(title: String, busy: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = { if (!busy) onClick() }, enabled = enabled, modifier = modifier) {
        Box(Modifier.widthIn(min = 140.dp), contentAlignment = Alignment.Center) {
            Text(title, modifier = Modifier.alpha(if (busy) 0f else 1f))
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
        }
    }
}

/**
 * A form field: Material 3's text field (Compose for TV has none). Focus alone doesn't open the
 * system IME, as on a TV: Select starts editing, and the IME action, Back or leaving the field
 * ends it, so D-pad focus can always move on.
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onSubmit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(false) }
    fun finish() {
        editing = false
        keyboard?.hide()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { androidx.compose.material3.Text(label) },
        placeholder = placeholder?.let { { androidx.compose.material3.Text(it) } },
        singleLine = true,
        // Read-only keeps the IME closed while the field merely has focus.
        readOnly = !editing,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onAny = {
            finish()
            onSubmit?.invoke()
            if (onSubmit == null && imeAction == ImeAction.Next) focus.moveFocus(FocusDirection.Down)
        }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
            cursorColor = Accent,
        ),
        modifier = modifier.fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) editing = false }
            .onPreviewKeyEvent { e ->
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (editing) return@onPreviewKeyEvent false
                        if (e.type == KeyEventType.KeyUp) {
                            editing = true
                            keyboard?.show()
                        }
                        true
                    }
                    // Back closes the IME before it reaches the page; one more Back while
                    // editing ends editing rather than leaving the page.
                    Key.Back -> if (editing) { if (e.type == KeyEventType.KeyUp) finish(); true } else false
                    // A single-line field keeps Up / Down otherwise; on a remote they leave it, as a TV form expects.
                    Key.DirectionDown, Key.DirectionUp -> {
                        if (e.type == KeyEventType.KeyDown) focus.moveFocus(if (e.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up)
                        true
                    }
                    else -> false
                }
            },
    )
}

/** The server's uploaded logo (`/api/branding`), else the bundled MediaStorm logo. */
@Composable
fun BrandMark(height: Dp) {
    val brand by AppSession.brandLogoUrl.collectAsState()
    val modifier = Modifier.height(height).widthIn(max = height * 3)
    if (brand != null) {
        SubcomposeAsyncImage(
            model = brand,
            contentDescription = "MediaStorm",
            contentScale = ContentScale.Fit,
            error = { BundledLogo(Modifier.fillMaxSize()) },
            modifier = modifier,
        )
    } else {
        BundledLogo(modifier)
    }
}

@Composable
private fun BundledLogo(modifier: Modifier) {
    Image(painterResource(R.drawable.brand_logo), contentDescription = "MediaStorm", contentScale = ContentScale.Fit, modifier = modifier)
}
