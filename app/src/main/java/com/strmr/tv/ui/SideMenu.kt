package com.strmr.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.tv.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import com.strmr.tv.api.Profile
import com.strmr.tv.ui.onboarding.ProfileAvatar

/**
 * The side menu style, Expo's TV drawer on Compose for TV's [ModalNavigationDrawer]: a collapsed
 * icon rail beside the page that expands over it (the page keeps the rail's width, so it never
 * reflows) while focus is in it. Left from the page's left edge enters it on the current
 * entry; Select switches to an entry and moves focus into its page; Right goes back to the
 * page. The profile sits at the top and Settings last, as in Expo. With the rail hidden it
 * shrinks to an invisible sliver when closed, so it shows only while open.
 */
@Composable
internal fun SideMenu(
    tabs: List<ShellTab>,
    selected: Int,
    profile: Profile?,
    showRail: Boolean,
    onSelect: (ShellTab) -> Unit,
    onProfile: () -> Unit,
    menuFocus: FocusRequester,
    pageFocus: FocusRequester,
    onMenuFocused: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    // The closed rail's width, reserved beside the page: the open drawer draws over the page, so it never reflows.
    // The drawer reports Closed as soon as it starts closing, while its width still animates down from the
    // open width, so only the narrowest closed width counts: the inset never follows the close animation.
    // Measured again when the rail is shown or hidden, or the profile entry appears.
    var railWidth by remember(showRail, profile != null) { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }
    val density = LocalDensity.current
    // Follows focus: the sheet opens itself only on focus it sees after its first frame, so focus the
    // system restores into the rail at launch would otherwise leave it closed (or invisible) while focused.
    val drawerState = remember { DrawerState(DrawerValue.Closed) }
    ModalNavigationDrawer(
        drawerContent = { value ->
            val open = value == DrawerValue.Open
            val background by animateColorAsState(
                when {
                    open -> Color(0xF00B0B0D)
                    showRail -> Color.Black.copy(alpha = 0.45f)
                    else -> Color.Transparent
                },
                label = "drawer",
            )
            Column(
                Modifier.fillMaxHeight()
                    // Hidden rail: a 1 dp sliver Left can still reach, drawn only while open.
                    .then(if (open || showRail) Modifier else Modifier.widthIn(max = 1.dp).clipToBounds().alpha(0f))
                    .onSizeChanged {
                        if (open) return@onSizeChanged
                        val width = with(density) { it.width.toDp() }
                        if (railWidth.let { w -> w == null || width < w }) railWidth = width
                    }
                    .background(background)
                    // More entries than the screen holds scroll instead of squeezing the last ones.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 28.dp)
                    // Entering from the page lands on the current entry, not the one beside the focused card.
                    .focusProperties { onEnter = { menuFocus.requestFocus() } }
                    .onFocusChanged {
                        if (it.hasFocus != (drawerState.currentValue == DrawerValue.Open)) {
                            drawerState.setValue(if (it.hasFocus) DrawerValue.Open else DrawerValue.Closed)
                        }
                        onMenuFocused(it.hasFocus)
                    }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (profile != null) {
                    ProfileEntry(profile, onProfile, Modifier.focusProperties { right = pageFocus })
                    Spacer(Modifier.height(16.dp))
                }
                tabs.forEachIndexed { index, tab ->
                    Entry(
                        tab.title, tab.icon, index == selected, onClick = { onSelect(tab) },
                        modifier = Modifier.focusProperties { right = pageFocus }
                            .then(if (index == selected) Modifier.focusRequester(menuFocus) else Modifier),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        drawerState = drawerState,
        scrimBrush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)),
    ) {
        Box(Modifier.fillMaxSize().padding(start = railWidth ?: 0.dp)) { content() }
    }
}

/** One entry: the icon on the rail, the label beside it once open; the current one accent-tinted (Expo's active pill). */
@Composable
private fun NavigationDrawerScope.Entry(title: String, icon: ImageVector, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    NavigationDrawerItem(
        selected = active,
        onClick = onClick,
        leadingContent = { Icon(icon, contentDescription = null, tint = if (active && !focused) Accent else LocalContentColor.current) },
        modifier = modifier,
        interactionSource = interaction,
        shape = NavigationDrawerItemDefaults.shape(RoundedCornerShape(50)),
        colors = entryColors(),
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The profile's avatar; open, its name over "Switch Profile". Select opens the profile picker. */
@Composable
private fun NavigationDrawerScope.ProfileEntry(profile: Profile, onClick: () -> Unit, modifier: Modifier) {
    NavigationDrawerItem(
        selected = false,
        onClick = onClick,
        leadingContent = {
            Box(Modifier.size(NavigationDrawerItemDefaults.IconSize + 8.dp), contentAlignment = Alignment.Center) {
                ProfileAvatar(profile, NavigationDrawerItemDefaults.IconSize + 8.dp)
            }
        },
        supportingContent = { Text("Switch Profile", maxLines = 1) },
        modifier = modifier,
        shape = NavigationDrawerItemDefaults.shape(RoundedCornerShape(50)),
        colors = entryColors(),
    ) {
        Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun entryColors() = NavigationDrawerItemDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = Color.White.copy(alpha = 0.85f),
    inactiveContentColor = Color.White.copy(alpha = 0.7f),
    focusedContainerColor = Color.White,
    focusedContentColor = Color.Black,
    selectedContainerColor = Accent.copy(alpha = 0.16f),
    selectedContentColor = Color.White,
    focusedSelectedContainerColor = Color.White,
    focusedSelectedContentColor = Color.Black,
)
