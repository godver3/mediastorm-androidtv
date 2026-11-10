package com.strmr.mobile.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.strmr.mobile.ui.components.PosterCard
import com.strmr.mobile.ui.components.TitleMenu
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.Route
import com.strmr.tv.api.Artwork
import com.strmr.tv.api.CastMember
import com.strmr.tv.api.PersonRef
import com.strmr.tv.api.Title
import com.strmr.tv.app.AppSession
import com.strmr.tv.home.HomeCard
import com.strmr.tv.ui.details.DetailsModel

/**
 * The rows under the episodes, in the Expo order: Part of {collection} (movies, release order),
 * More Like This and Cast, opening details or a person page. A kids profile's cards don't open
 * anything, as on the TV and iPhone apps.
 */
fun LazyListScope.relatedRows(model: DetailsModel) {
    model.title?.collection?.let { collection ->
        if (model.collectionMovies.isNotEmpty()) item(key = "collection") { PosterRow("Part of ${collection.name}", model.collectionMovies) }
    }
    if (model.similar.isNotEmpty()) item(key = "similar") { PosterRow("More Like This", model.similar.take(20)) }
    val cast = model.cast
    if (cast.isNotEmpty()) item(key = "cast") { CastRow(cast) }
}

@Composable
private fun opensRelated(): Boolean {
    val profile by AppSession.profile.collectAsState()
    return profile?.isKidsProfile != true
}

@Composable
private fun RelatedHeading(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
    )
}

@Composable
private fun PosterRow(heading: String, titles: List<Title>) {
    val navigator = LocalNavigator.current
    val opens = opensRelated()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RelatedHeading(heading)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(titles, key = { it.id }) { title ->
                val card = HomeCard.from(title)
                PosterCard(
                    card, 116.dp,
                    onClick = { if (opens) card.target?.let { navigator.openDetails(it) } },
                    onLongClick = if (opens) { { TitleMenu.show(card) } } else null,
                )
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<CastMember>) {
    val navigator = LocalNavigator.current
    val opens = opensRelated()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RelatedHeading("Cast")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cast, key = { it.id }) { member ->
                Column(
                    Modifier.width(92.dp).clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = opens) { navigator.push(Route.Person(PersonRef(member.id, member.name, member.profileUrl))) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(84.dp).clip(CircleShape)) { CastPhoto(member.profileUrl, 84.dp) }
                    Text(member.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                         overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    member.character?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                             overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** A person's portrait filling its box, or a person symbol when there is none. */
@Composable
fun CastPhoto(url: String?, size: Dp) {
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.4f))
        }
    }
    if (url == null) placeholder()
    else SubcomposeAsyncImage(Artwork.url(url, "w342"), null, contentScale = ContentScale.Crop, error = { placeholder() }, modifier = Modifier.fillMaxSize())
}
