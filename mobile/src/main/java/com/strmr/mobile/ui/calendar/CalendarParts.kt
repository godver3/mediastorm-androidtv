package com.strmr.mobile.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.ArtImage
import com.strmr.mobile.ui.components.TitleMenu
import com.strmr.mobile.ui.components.cardClicks
import com.strmr.tv.calendar.CalendarCategory
import com.strmr.tv.calendar.CalendarDay
import com.strmr.tv.calendar.CalendarModel
import com.strmr.tv.home.CalendarItem
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** All, TV, Movies, Trending, Watchlist, Top. */
@Composable
fun CategoryChips(selected: CalendarCategory, onSelect: (CalendarCategory) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(CalendarCategory.entries, key = { it.name }) { category ->
            FilterChip(selected = category == selected, onClick = { onSelect(category) }, label = { Text(category.label) })
        }
    }
}

/** The days with releases, weekday over date; the one the agenda shows is filled and kept in view. */
@Composable
fun DayStrip(days: List<CalendarDay>, selected: String?, onDay: (String) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected, days) {
        val index = days.indexOfFirst { it.key == selected }
        if (index < 0) return@LaunchedEffect
        val visible = state.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == index } || visible.first().index == index || visible.last().index == index) {
            state.animateScrollToItem((index - 2).coerceAtLeast(0))
        }
    }
    LazyRow(
        state = state, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        items(days, key = { it.key }) { day ->
            val isSelected = day.key == selected
            val date = CalendarModel.parse(day.key)
            val content = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier.size(width = 46.dp, height = 58.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onDay(day.key) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    date?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.getDefault()).orEmpty(),
                    style = MaterialTheme.typography.labelSmall, color = content,
                )
                Text(date?.format(DateTimeFormatter.ofPattern("d")) ?: day.key, style = MaterialTheme.typography.titleMedium, color = content)
                Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(content.copy(alpha = 0.8f)))
            }
        }
    }
}

/** One release: its poster, the time and source, the title, the episode and a summary. */
@Composable
fun CalendarRow(item: CalendarItem, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().cardClicks(onOpen) { TitleMenu.show(card(item)) }.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            ArtImage(item.textPosterUrl ?: item.posterUrl, "w342", item.title, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    CalendarModel.formatTime(item.airTime), style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                )
                CalendarLayout.sourceLabel(item.source)?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.episodeTitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val line = listOfNotNull(CalendarModel.posterMetadata(item).takeIf { it.isNotEmpty() }, item.network?.takeIf { it.isNotBlank() })
            if (line.isNotEmpty()) {
                Text(
                    line.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                CalendarModel.summary(item), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The release as a card for the title menu: its title's details, and the episode it stands for. */
private fun card(item: CalendarItem) = HomeCard(
    id = CalendarModel.itemKey(item),
    link = HomeLink.Details(CalendarModel.target(item)),
    title = item.title,
    subtitle = CalendarModel.posterMetadata(item),
    posterUrl = item.posterUrl,
    backdropUrl = item.backdropUrl,
)
