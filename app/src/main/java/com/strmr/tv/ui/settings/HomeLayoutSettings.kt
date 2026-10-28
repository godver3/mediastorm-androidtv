package com.strmr.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.home.CardSize
import com.strmr.tv.home.CardText
import com.strmr.tv.home.FocusedCardPosition
import com.strmr.tv.home.HeroSize
import com.strmr.tv.home.HeroTextSize
import com.strmr.tv.home.CarouselPick
import com.strmr.tv.home.HomeLayoutStore
import com.strmr.tv.home.HomeLayoutOptions
import com.strmr.tv.home.ShelfLayoutInfo
import com.strmr.tv.home.ShelfStyle
import com.strmr.tv.home.ShelfTransition
import com.strmr.tv.ui.pageBackground

/**
 * Settings › Home Layout: each choice is this device's, over the profile's value ("Default"
 * follows it). "View Switcher on Home" shows the Home View pill under the tab bar. Select steps through the choices, as a tvOS option row does.
 */
@Composable
fun HomeLayoutSettings() {
    val s = HomeLayoutStore
    val profile = s.profile
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().pageBackground(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Home Layout", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            item { Header("Home View") }
            item {
                Choice("View Switcher on Home", onOff(s.viewSwitcher), Modifier.focusRequester(first)) { s.chooseViewSwitcher(!s.viewSwitcher) }
            }
            val options = HomeLayoutOptions(s.topCarousel, s.focusedInfo)
            item { Header("Carousel") }
            item {
                // An independent carousel from one row, above the rows.
                Choice("Top Carousel", onOff(s.topCarousel)) { s.chooseTopCarousel(!s.topCarousel) }
            }
            if (options.showsCarouselRow) {
                item {
                    val rows = s.shelves
                    val chosen = rows.firstOrNull { it.id == s.carouselRow }
                    Choice("Carousel Row", chosen?.title ?: "Default (${defaultCarouselRow(rows)})") {
                        // Default → each of the profile's rows → Default.
                        s.chooseCarouselRow(cycleNullable(chosen, rows)?.id)
                    }
                }
            }
            item { Header("Hero") }
            item {
                // The Classic hero: backdrop, logo, line and synopsis following the focused card.
                Choice("Focused Title Info", onOff(s.focusedInfo)) { s.chooseFocusedInfo(!s.focusedInfo) }
            }
            if (options.showsHeroSize) item {
                Choice("Hero Size", label(s.heroSize?.label, profile.heroSize.label)) {
                    s.chooseHeroSize(cycleNullable(s.heroSize, HeroSize.entries))
                }
            }
            // The full hero shows one shelf to a screen: Slide scrolls to the next, Swap fades to it.
            if (options.showsHeroSize && s.effectiveHeroSize == HeroSize.FULL) item {
                Choice("Shelf Transition", s.shelfTransition.label) {
                    s.chooseShelfTransition(cycle(s.shelfTransition, ShelfTransition.entries))
                }
            }
            if (options.showsTextSize) item {
                Choice(options.textSizeLabel, s.heroTextSize.label) { s.chooseHeroTextSize(cycle(s.heroTextSize, HeroTextSize.entries)) }
            }
            item { Header("Cards") }
            item {
                Choice("Focused Card Position", label(s.focusPosition?.label, profile.focusPosition.label)) {
                    s.chooseFocusPosition(cycleNullable(s.focusPosition, FocusedCardPosition.entries))
                }
            }
            item {
                Choice("Card Size", label(s.cardSize?.label, CardSize.nearest(profile.cardScale).label)) {
                    s.chooseCardSize(cycleNullable(s.cardSize, CardSize.entries))
                }
            }
            // One kind of row's card size; Default follows Card Size.
            items(ShelfStyle.entries, key = { "size-${it.name}" }) { style ->
                val chosen = s.styleCardSizes[style]
                Choice(style.cardSizeLabel, label(chosen?.label, CardSize.nearest(s.effectiveCardScale).label)) {
                    s.chooseStyleCardSize(style, cycleNullable(chosen, CardSize.entries))
                }
            }
            item { Choice("Card Titles", s.cardText.label) { s.chooseCardText(cycle(s.cardText, CardText.entries)) } }
            item {
                Choice("Posters Expand on Focus", label(s.expandPosters?.let(::onOff), onOff(profile.expandPosters))) {
                    s.chooseExpandPosters(when (s.expandPosters) { null -> true; true -> false; false -> null })
                }
            }
            if (s.shelves.isNotEmpty()) item { Header("Rows") }
            items(s.shelves, key = { it.id }) { shelf ->
                val hidden = s.isHidden(shelf.id)
                val chosen = s.shelfStyles[shelf.id]
                val value = if (hidden) "Hidden" else label(chosen?.label, s.defaultStyle(shelf.id).label)
                // Default → each style → Hidden → Default.
                Choice(shelf.title, value) {
                    when {
                        hidden -> { s.setHidden(false, shelf.id); s.setStyle(null, shelf.id) }
                        chosen == null -> s.setStyle(ShelfStyle.entries.first(), shelf.id)
                        chosen == ShelfStyle.entries.last() -> s.setHidden(true, shelf.id)
                        else -> s.setStyle(ShelfStyle.entries[chosen.ordinal + 1], shelf.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(selected = false, onClick = onClick, headlineContent = { Text(title) }, supportingContent = { Text(value) }, modifier = modifier)
}

@Composable
private fun Header(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 14.dp, start = 4.dp))
}

/** What Default shows: Top 10, else Trending, else the first row ([com.strmr.tv.home.CarouselPick]). */
private fun defaultCarouselRow(rows: List<ShelfLayoutInfo>): String =
    (rows.firstOrNull { it.id == CarouselPick.TOP_TEN } ?: rows.firstOrNull { it.id.startsWith("trending") } ?: rows.firstOrNull())?.title ?: "Top 10"

private fun label(chosen: String?, profile: String) = chosen ?: "Default ($profile)"

private fun onOff(value: Boolean) = if (value) "On" else "Off"

private fun <T> cycle(current: T, all: List<T>): T = all[(all.indexOf(current) + 1) % all.size]

/** null (Default) → each value → null. */
private fun <T> cycleNullable(current: T?, all: List<T>): T? {
    if (current == null) return all.first()
    val next = all.indexOf(current) + 1
    return if (next >= all.size) null else all[next]
}
