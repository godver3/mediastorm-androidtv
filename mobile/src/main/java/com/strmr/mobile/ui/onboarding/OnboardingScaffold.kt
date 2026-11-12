package com.strmr.mobile.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.strmr.core.R
import com.strmr.tv.app.AppSession

/**
 * Logo, title and subtitle over a centred, scrollable column (phones and tablets).
 * [profiles] shows the server's profile-picker image instead of its logo.
 */
@Composable
fun OnboardingScaffold(
    title: String,
    subtitle: String? = null,
    profiles: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrandLogo(Modifier.height(96.dp), profiles = profiles)
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/** The server's uploaded logo (`/api/branding`), else the bundled MediaStorm logo. */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, profiles: Boolean = false) {
    val branding by AppSession.branding.collectAsState()
    val brand = branding?.let { if (profiles) it.profilePickerPath(tv = false) else it.logoPath }
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
