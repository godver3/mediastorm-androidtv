package com.strmr.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.ui.cardFocusBorder
import com.strmr.tv.api.Artwork
import com.strmr.tv.api.CastMember
import com.strmr.tv.api.PersonRef
import com.strmr.tv.api.Title
import com.strmr.tv.app.AppSession
import com.strmr.tv.home.HomeCard
import com.strmr.tv.ui.CardFocusScale
import com.strmr.tv.ui.captionGap
import com.strmr.tv.ui.ContentMargin
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.MessageDialog
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.PosterCard
import com.strmr.tv.ui.TitleMenu

/** Related cards' width (tvOS 200 pt). */
private val CARD_WIDTH = 100.dp

/**
 * The rows under the episodes, in the Expo TV order: Part of {collection} (movies, release
 * order), More Like This and Cast, opening details or a person page. A kids profile's cards
 * don't open anything, as on tvOS.
 */
@Composable
fun RelatedRows(model: DetailsModel, modifier: Modifier = Modifier) {
    val profile by AppSession.profile.collectAsState()
    val opens = profile?.isKidsProfile != true
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<HomeCard?>(null) }
    var menuError by remember { mutableStateOf<String?>(null) }

    fun LazyListScope.posters(titles: List<Title>) = items(titles, key = { it.id }) { title ->
        val card = HomeCard.from(title)
        Box(Modifier.width(CARD_WIDTH)) {
            PosterCard(card, onClick = { if (opens) card.target?.let(navigator::openDetails) }, onLongClick = { if (opens) menuFor = card })
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        model.title?.collection?.let { collection ->
            if (model.collectionMovies.isNotEmpty()) RelatedRow("Part of ${collection.name}") { posters(model.collectionMovies) }
        }
        if (model.similar.isNotEmpty()) RelatedRow("More Like This") { posters(model.similar.take(20)) }
        val cast = model.cast
        if (cast.isNotEmpty()) RelatedRow("Cast") {
            items(cast, key = { it.id }) { member ->
                CastCard(member) { if (opens) navigator.open(Page.Person(PersonRef(member.id, member.name, member.profileUrl))) }
            }
        }
    }
    menuFor?.let { card -> TitleMenu(card, scope, onError = { menuError = it }) { menuFor = null } }
    menuError?.let { MessageDialog("Couldn't Update", it) { menuError = null } }
}

@Composable
private fun RelatedRow(heading: String, cards: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = ContentMargin))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ContentMargin, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.focusRestorer(),
            content = cards,
        )
    }
}

@Composable
private fun CastCard(member: CastMember, onClick: () -> Unit) {
    Column(Modifier.width(CARD_WIDTH), verticalArrangement = Arrangement.spacedBy(captionGap(CARD_WIDTH * 1.5f))) {
        Card(onClick = onClick, scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(), modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            CastPhoto(member.profileUrl, CARD_WIDTH)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            member.character?.takeIf { it.isNotEmpty() }?.let {
                Text(it, fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A cast member's portrait, or a person symbol when there is none. */
@Composable
fun CastPhoto(url: String?, width: Dp) {
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize().background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(width * 0.35f))
        }
    }
    if (url == null) placeholder()
    else SubcomposeAsyncImage(Artwork.url(url, "w342"), null, contentScale = ContentScale.Crop, error = { placeholder() }, modifier = Modifier.fillMaxSize())
}
