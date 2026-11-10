package com.strmr.mobile.ui.person

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.PosterCard
import com.strmr.mobile.ui.components.TitleMenu
import com.strmr.mobile.ui.components.rememberCardOpener
import com.strmr.mobile.ui.details.CastPhoto
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.PersonDetails
import com.strmr.tv.api.PersonRef
import com.strmr.tv.api.person
import com.strmr.tv.home.HomeCard

/**
 * A cast member's page, opened from a details page's Cast row (or Search): their photo, what
 * they are known for and their biography (tap for all of it), over a grid of the titles they
 * appear in.
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
            if (details == null) loadError = e.message ?: "Couldn't load this person."
        }
    }
    val titles = details?.filmography.orEmpty().map(HomeCard::from)
    val open = rememberCardOpener()
    val wide = LocalConfiguration.current.smallestScreenWidthDp >= 600

    PageScaffold(person.name, showBack = true) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(if (wide) 140.dp else 104.dp),
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Header(details?.person, person)
                    when {
                        loadError != null -> Text(loadError!!, color = MaterialTheme.colorScheme.error)
                        details == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        titles.isEmpty() -> Text("No titles found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Text("Known For", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            items(titles, key = { it.id }) { card ->
                PosterCard(card, width = Dp.Unspecified, onClick = { open(card) }, onLongClick = { TitleMenu.show(card) },
                           modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Header(info: PersonDetails.Person?, ref: PersonRef) {
    val photo = info?.profileUrl ?: ref.profileUrl
    val bio = info?.biography?.trim()?.ifEmpty { null }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(100.dp, 150.dp).clip(RoundedCornerShape(10.dp))) { CastPhoto(photo, 100.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            info?.knownFor?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            bio?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize().clickable(onClickLabel = if (expanded) "Show less" else "Show more") { expanded = !expanded },
                )
            }
        }
    }
}
