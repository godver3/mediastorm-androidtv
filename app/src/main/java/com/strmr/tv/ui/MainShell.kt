package com.strmr.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.MenuStyle
import com.strmr.tv.app.MenuTabs
import com.strmr.tv.home.HomeLayoutStore
import com.strmr.tv.home.HomeViewStore
import com.strmr.tv.home.HomeViews
import com.strmr.tv.launcher.DeepLink
import com.strmr.tv.launcher.DeepLinkRouter
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.ListsTabStore
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.ui.calendar.CalendarScreen
import com.strmr.tv.ui.home.HomeScreen
import com.strmr.tv.ui.lists.ListsScreen
import com.strmr.tv.ui.live.LiveScreen
import com.strmr.tv.ui.recordings.RecordingsScreen
import com.strmr.tv.ui.sports.SportsScreen
import com.strmr.tv.ui.search.SearchScreen
import com.strmr.tv.ui.home.HomeState
import com.strmr.tv.ui.settings.SettingsScreen
import com.strmr.tv.ui.watchlist.WatchlistScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The signed-in shell: the menu (Google TV's top tabs, or a side menu: Settings › Menu ›
 * Menu Style) and the page. Back from inside a page returns focus to the menu, and Back on the
 * menu leaves the app, as system TV apps do. With "View Switcher on Home" on (a device
 * setting) and a view without its own tab, a pill floats over Home's top and opens the Home
 * view picker: Down from the tabs lands on it, Up from Home's rows comes back to it.
 */
@Composable
fun MainShell() {
    val profile by AppSession.profile.collectAsState()
    remember(profile?.id) { HomeViewStore.activate(ApiClient.profileId) }
    val viewTabs = HomeViewTabs.shown()
    val homeView = HomeViewStore.home
    val tabs = ShellTabs.build(profile, viewTabs)
    // The tab's key: a page's name, or "view:<id>" for a view's own tab. The app always opens on Home (as on tvOS).
    var selectedKey by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    val selected = ShellTabs.indexOf(tabs, selectedKey)
    val onHomeTab = tabs[selected].key == MainTab.HOME.name
    LaunchedEffect(tabs[selected].key) { selectedKey = tabs[selected].key }
    // Home shows its own remembered view (only views without a tab); one that gets its own tab falls back to All.
    LaunchedEffect(onHomeTab, homeView, homeView in viewTabs) {
        if (onHomeTab) HomeViewStore.select(if (homeView in viewTabs) HomeViews.ALL else homeView)
    }
    val side = MenuStyle.side
    LaunchedEffect(side) { AppLog.i("Menu", "style ${if (side) "side" else "top"} (rail ${MenuStyle.showsRail})") }
    var menuFocused by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    val pageFocus = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    // Side menu: Select on an entry moves focus into its page once the page has something to focus.
    var enterPage by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var pickerClosed by remember { mutableIntStateOf(0) }
    val switcherFocus = remember { FocusRequester() }
    var switcherFocused by remember { mutableStateOf(false) }
    val onHome = tabs[selected].page == MainTab.HOME
    // Over Home's top, not part of its layout, so turning it on moves nothing.
    val showsSwitcher = onHomeTab && HomeLayoutStore.viewSwitcher && HomeViewTabs.untabbed(viewTabs).isNotEmpty()
    val scope = rememberCoroutineScope()
    // Home's rows and focus memory outlive its page, which leaves composition while another tab is up.
    val home = remember { HomeState(scope) }
    LaunchedEffect(Unit) { MenuTabs.refresh(ApiClient.profileId) }
    LaunchedEffect(Unit) { WatchlistStore.refresh() }
    LaunchedEffect(Unit) { ListsTabStore.refresh() }
    // A home screen row's link (now, or held from a cold start, sign-in or the profile picker).
    val navigator = LocalNavigator.current
    val link by DeepLinkRouter.pending.collectAsState()
    LaunchedEffect(link) {
        val taken = DeepLinkRouter.take(profile?.id) ?: return@LaunchedEffect
        navigator.openFromLink(taken.target, autoplay = taken.action == DeepLink.Action.PLAY)
    }
    LaunchedEffect(pickerClosed) {
        if (pickerClosed == 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { switcherFocus.requestFocus() }
    }
    LaunchedEffect(enterPage) {
        if (enterPage == 0) return@LaunchedEffect
        // Up to 3 s for a loading page; given up if focus leaves the menu some other way.
        repeat(180) {
            withFrameNanos { }
            if (pageFocused || !menuFocused) return@LaunchedEffect
            runCatching { pageFocus.requestFocus() }
        }
    }

    // A Back before a pending Add to List… has focus cancels it, not moving focus.
    val listPickerPending = CustomListsStore.picking != null
    BackHandler(enabled = !menuFocused || listPickerPending) {
        if (listPickerPending) CustomListsStore.picking = null else runCatching { menuFocus.requestFocus() }
    }
    // Just after a profile opens, a quick Back (or two) lands while Home is still loading: the first
    // moves focus to the menu, which keeps Home from taking it, and the next would leave the app. Until
    // focus first reaches the page (at most 10 s, so a Home that never loads can still be left), Back on
    // the menu does nothing.
    var settling by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(10_000); settling = false }
    LaunchedEffect(pageFocused) { if (pageFocused) settling = false }
    BackHandler(enabled = settling && menuFocused && !listPickerPending) {}

    val switcher: (@Composable (Modifier) -> Unit)? = if (!showsSwitcher) null else { modifier ->
        HomeViewSwitcher(
            onClick = { picking = true },
            modifier = modifier.focusRequester(switcherFocus)
                .focusProperties { down = pageFocus }
                .onFocusChanged { switcherFocused = it.hasFocus },
        )
    }
    val page: @Composable () -> Unit = {
        // One focus group per page: the menu enters it on the control last focused there.
        Box(
            Modifier.fillMaxSize().focusRequester(pageFocus)
                // Up out of Home's top row goes to the view switcher, not the tab above the card.
                .focusProperties {
                    onExit = {
                        if (showsSwitcher && requestedFocusDirection == FocusDirection.Up) {
                            cancelFocusChange()
                            runCatching { switcherFocus.requestFocus() }
                        }
                    }
                }
                .onFocusChanged { pageFocused = it.hasFocus }
                .focusRestorer(),
        ) {
            when (tabs[selected].page) {
                // While the picker is open or the switcher focused, a new view's rows don't take focus.
                MainTab.HOME -> HomeScreen(home, tabsFocused = { menuFocused || picking || switcherFocused })
                MainTab.LIVE -> LiveScreen()
                MainTab.WATCHLIST -> WatchlistScreen()
                MainTab.LISTS -> ListsScreen()
                MainTab.CALENDAR -> CalendarScreen()
                MainTab.RECORDINGS -> RecordingsScreen()
                MainTab.SPORTS -> SportsScreen()
                MainTab.SEARCH -> SearchScreen()
                MainTab.SETTINGS -> SettingsScreen()
            }
        }
    }

    // Home draws its own backdrop; the other tabs sit on the tvOS page grey.
    Box(Modifier.fillMaxSize().then(if (onHome) Modifier.background(Color.Black) else Modifier.pageBackground())) {
        if (side) {
            SideMenu(
                tabs, selected, profile, MenuStyle.showsRail,
                onSelect = { tab ->
                    selectedKey = tab.key
                    ShellTabs.showView(tab, viewTabs)
                    enterPage++
                },
                onProfile = { scope.launch { AppSession.switchProfile() } },
                menuFocus = menuFocus, pageFocus = pageFocus, onMenuFocused = { menuFocused = it },
            ) {
                Box(Modifier.fillMaxSize()) {
                    page()
                    // Top centre of the page, where it sits under the tab bar in the top style.
                    switcher?.invoke(Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
                }
            }
        } else {
            page()
            TopMenuBar(
                tabs, selected,
                onFocusTab = { tab ->
                    selectedKey = tab.key
                    ShellTabs.showView(tab, viewTabs)
                },
                menuFocus = menuFocus, pageFocus = pageFocus, onMenuFocused = { menuFocused = it },
                switcher = switcher, switcherFocus = switcherFocus,
            )
        }
    }
    if (picking) {
        HomeViewPicker(
            views = HomeViewTabs.untabbed(viewTabs),
            onPick = { HomeViewStore.select(it) },
            onDismiss = {
                picking = false
                pickerClosed++
            },
        )
    }
}
