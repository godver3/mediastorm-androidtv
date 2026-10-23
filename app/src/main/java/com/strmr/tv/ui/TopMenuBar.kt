package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text

/**
 * The top menu style: Google TV's tab row on a glass pill over the page. Focusing a tab
 * switches the page under it; Down enters the page (the view switcher first, on Home). The
 * switcher, when shown, floats just under the tabs. When every entry with its icon doesn't fit
 * the screen (many optional tabs and Home views shown), the entries drop their icons and
 * close up; past that the row scrolls, so Settings at its end is always reachable.
 */
@Composable
internal fun TopMenuBar(
    tabs: List<ShellTab>,
    selected: Int,
    onFocusTab: (ShellTab) -> Unit,
    menuFocus: FocusRequester,
    pageFocus: FocusRequester,
    onMenuFocused: (Boolean) -> Unit,
    switcher: (@Composable (Modifier) -> Unit)?,
    switcherFocus: FocusRequester,
) {
    var width by remember { mutableIntStateOf(0) }
    // The entries this row was too wide for (with icons): compact until the entries change.
    var compactFor by remember { mutableStateOf<List<String>?>(null) }
    val keys = tabs.map { it.key }
    val compact = compactFor == keys
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).onSizeChanged { width = it.width }) {
        // On a glass pill, so it reads over any backdrop (the tvOS tab bar's look).
        TabRow(
            selectedTabIndex = selected,
            modifier = Modifier.align(Alignment.CenterHorizontally).wrapContentWidth()
                // Held to the screen's width means it overflowed (it scrolls): go compact.
                .onSizeChanged { if (!compact && width > 0 && it.width >= width) compactFor = keys }
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 4.dp)
                // Up from anywhere in the page lands on the current tab, not the one nearest the focused card.
                .focusProperties { onEnter = { menuFocus.requestFocus() } }
                .onFocusChanged { onMenuFocused(it.hasFocus) },
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selected,
                    onFocus = { onFocusTab(tab) },
                    // Labels stay readable while focus is in the page, as on tvOS.
                    colors = TabDefaults.pillIndicatorTabColors(
                        contentColor = Color.White.copy(alpha = 0.8f),
                        inactiveContentColor = Color.White.copy(alpha = 0.8f),
                    ),
                    // Down enters the page (the view switcher first, on Home): the search alone doesn't leave the row for it.
                    modifier = Modifier.focusProperties { down = if (switcher != null) switcherFocus else pageFocus }
                        .then(if (index == selected) Modifier.focusRequester(menuFocus) else Modifier),
                ) {
                    Row(
                        Modifier.padding(horizontal = if (compact) 10.dp else 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!compact) Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(tab.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        switcher?.invoke(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).focusProperties { up = menuFocus })
    }
}
