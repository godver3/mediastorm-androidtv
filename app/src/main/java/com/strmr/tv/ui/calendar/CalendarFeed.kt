package com.strmr.tv.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.calendar.CalendarDay
import com.strmr.tv.calendar.CalendarModel
import com.strmr.tv.calendar.CalendarTimeline
import com.strmr.tv.home.CalendarItem
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.ui.MinimalScroll
import com.strmr.tv.ui.PosterCard
import com.strmr.tv.ui.clipVertically
import kotlinx.coroutines.flow.first

/** Every day section is this tall (Expo `getCalendarTVLayout().sectionHeight`), so the list can centre one. */
private val SectionHeight = 262.dp
private val PosterWidth = 104.dp

/**
 * The release feed (Expo `CalendarReleaseFeed`, TV): a day a section, a heading line and a
 * row of posters. It opens with the selected day centred, and keeps the focused day centred
 * as focus moves between days. Focusing a poster selects it; Select opens its details.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarFeed(
    days: List<CalendarDay>,
    listState: LazyListState,
    activeItemKey: String?,
    activePoster: FocusRequester,
    /** Left from a day's first poster: the rail's selected entry, not the nearest one. */
    rail: FocusRequester,
    onPreview: (CalendarItem) -> Unit,
    onOpen: (CalendarItem) -> Unit,
    /** Long press: the title menu for the poster's card. */
    onMenu: (HomeCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Opening position: the selected day centred (it starts at the top of the list until measured).
    LaunchedEffect(listState) {
        val viewport = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        val section = with(density) { SectionHeight.roundToPx() }
        listState.scrollToItem(listState.firstVisibleItemIndex, -((viewport - section) / 2).coerceAtLeast(0))
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides CenterScroll) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 20.dp),
            modifier = modifier.clipVertically(),
        ) {
            items(days, key = { it.key }) { day -> DaySection(day, activeItemKey, activePoster, rail, onPreview, onOpen, onMenu) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DaySection(
    day: CalendarDay,
    activeItemKey: String?,
    activePoster: FocusRequester,
    rail: FocusRequester,
    onPreview: (CalendarItem) -> Unit,
    onOpen: (CalendarItem) -> Unit,
    onMenu: (HomeCard) -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(SectionHeight), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(26.dp).padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(CalendarModel.fullDayHeading(day.key), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                 overflow = TextOverflow.Ellipsis)
            Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.14f)))
            Text(CalendarTimeline.releaseCount(day.items.size), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                 color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(end = 16.dp))
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().focusRestorer(),
            ) {
                itemsIndexed(day.items, key = { _, it -> CalendarModel.itemKey(it) }) { index, item ->
                    val key = CalendarModel.itemKey(item)
                    val card = item.card(key)
                    Box(Modifier.width(PosterWidth)) {
                        PosterCard(
                            card,
                            onClick = { onOpen(item) },
                            // The title menu, as on Home and in lists (tvOS has it here too).
                            onLongClick = { onMenu(card) },
                            modifier = Modifier.onFocusChanged { if (it.isFocused) onPreview(item) }
                                .then(if (index == 0) Modifier.focusProperties { left = rail } else Modifier)
                                .then(if (key == activeItemKey) Modifier.focusRequester(activePoster) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

/** A poster card: the poster with its title text, the title and the episode code or release type. */
private fun CalendarItem.card(key: String) = HomeCard(
    id = key,
    link = HomeLink.Details(CalendarModel.target(this)),
    title = title,
    subtitle = CalendarModel.posterMetadata(this),
    posterUrl = textPosterUrl ?: posterUrl,
    episode = cardEpisode,
)

/** Keeps the focused day centred, as the Expo TV feed's centre snap does. */
@OptIn(ExperimentalFoundationApi::class)
private val CenterScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset + size / 2f - containerSize / 2f
}
