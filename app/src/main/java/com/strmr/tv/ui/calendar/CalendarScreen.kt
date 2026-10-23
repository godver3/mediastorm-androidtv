package com.strmr.tv.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.AppSession
import com.strmr.tv.calendar.CalendarCategory
import com.strmr.tv.calendar.CalendarModel
import com.strmr.tv.home.HomeCard
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.MessageDialog
import com.strmr.tv.ui.TitleMenu

/**
 * The Calendar tab (the Expo TV `calendar` screen): a rail of categories (All, TV, Movies,
 * Trending, Watchlist, Top) beside the date timeline and the release feed, with the focused
 * release's details in a panel at the right. Entering the page (Down from the tabs) lands on
 * the selected release, as on tvOS (the rail's selected entry while there is none); Right
 * from the rail goes to the selected release. Long press opens the title menu.
 */
@Composable
fun CalendarScreen() {
    val profile by AppSession.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val state = remember(profile?.id) { CalendarState(scope) }
    val loader = remember(profile?.id) { CalendarDetailLoader(scope) }
    LaunchedEffect(state) { state.load() }
    val days = state.days
    val selection = state.selection
    val active = state.activeItem
    LaunchedEffect(active) { loader.show(active) }

    val railFocus = remember { FocusRequester() }
    val activePoster = remember { FocusRequester() }
    val navigator = LocalNavigator.current
    val browsing = days.isNotEmpty()
    var menuFor by remember { mutableStateOf<HomeCard?>(null) }
    var menuError by remember { mutableStateOf<String?>(null) }

    // Focus coming into the page (Down from the tabs, Back from details) lands on the selected
    // release, as on tvOS; the rail's selected entry while there is none.
    var hasFocus by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxSize().padding(top = 76.dp)
            .onFocusChanged {
                val entered = it.hasFocus && !hasFocus
                hasFocus = it.hasFocus
                if (entered && browsing && active != null) runCatching { activePoster.requestFocus() }
            }
            .focusRestorer(railFocus)
            // One target for the whole page, so moving between the rail and the feed isn't an entry.
            .focusGroup(),
    ) {
        CalendarRail(
            selected = state.category,
            onSelect = state::select,
            selectedFocus = railFocus,
            // Right goes to the selected release, or the error's Try again; never up to the tabs.
            right = when {
                browsing && active != null -> activePoster
                state.error != null && state.items.isEmpty() -> FocusRequester.Default
                else -> FocusRequester.Cancel
            },
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                state.loading && state.items.isEmpty() -> StateMessage(null, null, "Loading calendar...", loading = true)
                state.error != null && state.items.isEmpty() -> StateMessage(
                    "Calendar unavailable", AppIcons.Event, state.error!!,
                ) { OutlinedButton(onClick = state::load) { Text("Try again") } }
                !browsing -> StateMessage(
                    "No calendar items", AppIcons.Event, "Try another filter or enable more calendar sources for this profile.",
                )
                else -> {
                    // A new list (category or reload) opens on the selected day.
                    val listState = remember(days) {
                        LazyListState(days.indexOfFirst { it.key == selection.dateKey }.coerceAtLeast(0))
                    }
                    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = CalendarPanelWidth)) {
                        CalendarTimelineView(days, selection.dateKey, Modifier.padding(top = 4.dp, bottom = 6.dp))
                        CalendarFeed(
                            days, listState, selection.itemKey, activePoster, railFocus,
                            onPreview = state::preview,
                            onMenu = { menuFor = it },
                            onOpen = { item ->
                                AppLog.i(LOG, "open ${item.titleId} (${CalendarModel.itemKey(item)})")
                                navigator.openDetails(CalendarModel.target(item))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CalendarDetailPanel(
                        loader.detail, loader.loading,
                        Modifier.align(Alignment.TopEnd).width(CalendarPanelWidth).fillMaxHeight(),
                    )
                }
            }
        }
    }
    menuFor?.let { card -> TitleMenu(card, scope, onError = { menuError = it }) { menuFor = null } }
    menuError?.let { MessageDialog("Couldn't Update", it) { menuError = null } }
}

/** The category rail (Expo `CalendarNavigationRail`): the selected entry has an accent bar, focus a light fill. */
@Composable
private fun CalendarRail(selected: CalendarCategory, onSelect: (CalendarCategory) -> Unit, selectedFocus: FocusRequester, right: FocusRequester) {
    Column(Modifier.width(184.dp).padding(start = 44.dp, end = 12.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Calendar", fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 2.dp, bottom = 12.dp))
        CalendarCategory.entries.forEach { category ->
            val isSelected = category == selected
            Surface(
                onClick = { onSelect(category) },
                modifier = Modifier.fillMaxWidth().height(32.dp)
                    .focusProperties { this.right = right }
                    .then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(3.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color(0xFFF4F4F4),
                    contentColor = if (isSelected) Accent else Color.White,
                    focusedContentColor = Color(0xFF111111),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(if (isSelected) Accent else Color.Transparent))
                    Text(category.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
                }
            }
        }
    }
}

/** Loading, error and empty states (Expo `CalendarState`), centred in the content area. */
@Composable
private fun StateMessage(
    title: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    message: String,
    loading: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
        icon?.let { Icon(it, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(36.dp)) }
        title?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
        Text(message, color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp))
        action?.invoke()
    }
}
