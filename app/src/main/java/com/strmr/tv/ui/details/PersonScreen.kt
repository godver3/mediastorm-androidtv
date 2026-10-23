package com.strmr.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.PersonDetails
import com.strmr.tv.api.PersonRef
import com.strmr.tv.api.person
import com.strmr.tv.home.HomeCard
import com.strmr.tv.ui.ExpandableText
import com.strmr.tv.ui.pageBackground
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.PosterGrid
import com.strmr.tv.ui.fullWidth

/**
 * A cast member's page, opened from the details page's Cast row: their photo and biography
 * over a grid of the titles they appear in (the Expo shelf's person mode).
 */
@Composable
fun PersonScreen(person: PersonRef) {
    var details by remember { mutableStateOf<PersonDetails?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(person.id) {
        try {
            val loaded = ApiClient.person(person.id)
            // A person can be credited twice on one title; the grid needs unique IDs.
            details = loaded.copy(filmography = loaded.filmography?.distinctBy { it.id })
            loadError = null
        } catch (e: Exception) {
            if (details == null) loadError = e.message
        }
    }
    val titles = details?.filmography.orEmpty().map(HomeCard::from)

    Box(Modifier.fillMaxSize().pageBackground()) {
        PosterGrid(
            titles,
            contentPadding = PaddingValues(horizontal = PageMargin, vertical = 32.dp),
            header = {
                fullWidth("header") {
                    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(person.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        details?.person?.let { Header(it, person) }
                        when {
                            loadError != null -> Text(loadError!!, color = Color(0xFFFF6B6B))
                            details == null -> CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            titles.isEmpty() -> Text("No titles found.", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun Header(info: PersonDetails.Person, ref: PersonRef) {
    val photo = info.profileUrl ?: ref.profileUrl
    val bio = info.biography?.trim()?.ifEmpty { null }
    if (photo == null && bio == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(100.dp, 150.dp).clip(RoundedCornerShape(6.dp))) { CastPhoto(photo, 100.dp) }
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            info.knownFor?.takeIf { it.isNotEmpty() }?.let {
                Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
            }
            bio?.let { ExpandableText(it, title = info.name, maxLines = 5, fontSize = 14.sp) }
        }
    }
}
