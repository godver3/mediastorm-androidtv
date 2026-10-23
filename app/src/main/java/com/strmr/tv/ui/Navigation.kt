package com.strmr.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.PersonRef
import com.strmr.tv.home.BrowseList
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.details.DetailsScreen
import com.strmr.tv.ui.details.PersonScreen
import com.strmr.tv.ui.lists.AddToListDialog
import com.strmr.tv.ui.lists.ListGridScreen
import com.strmr.tv.ui.recordings.RecordingsScreen
import com.strmr.tv.ui.settings.HomeLayoutSettings
import com.strmr.tv.ui.settings.SubtitleSettings
import com.strmr.tv.ui.settings.TopMenuSettings
import com.strmr.tv.ui.settings.UpdatePrompt

/** A page pushed over the tabs. */
sealed interface Page {
    /**
     * `openWatchState`: open the series watch-state sheet once the page has loaded (a menu's bulk action).
     * `autoplay`: start the primary action (Resume / Play) once loaded (a home screen row's play link).
     */
    data class Details(val target: DetailsTarget, val openWatchState: Boolean = false, val autoplay: Boolean = false) : Page
    /** `query`: extra list parameters, such as the Home view (`homeView`) the list was opened from. */
    data class Grid(val list: BrowseList, val query: Map<String, String> = emptyMap()) : Page
    data class Person(val person: PersonRef) : Page
    data object HomeLayout : Page
    data object TopMenu : Page
    data object SubtitleStyle : Page
    /** Live › More › Recordings (Expo's Live drawer entry); also a menu entry when Settings › Menu shows it. */
    data object Recordings : Page
}

/** Opens pages over the current screen. */
class Navigator(private val push: (Page) -> Unit, private val pop: () -> Unit = {}, private val reset: () -> Unit = {}) {
    fun openDetails(target: DetailsTarget, openWatchState: Boolean = false) {
        // Its stream starts resolving as the page opens, so Play starts quickly.
        PlaybackCoordinator.prewarm(target)
        push(Page.Details(target, openWatchState))
    }

    /** A home screen row's link: its details page alone over the tabs, whatever was open. */
    fun openFromLink(target: DetailsTarget, autoplay: Boolean) {
        reset()
        PlaybackCoordinator.prewarm(target)
        push(Page.Details(target, autoplay = autoplay))
    }

    fun openList(list: BrowseList, query: Map<String, String>? = null) = push(Page.Grid(list, query.orEmpty()))

    fun open(page: Page) = push(page)

    /** Closes the top page (a list deleted from its own grid). */
    fun close() = pop()
}

val LocalNavigator = staticCompositionLocalOf { Navigator(push = {}) }

/**
 * Pages pushed over the tabs. Each is a full-screen platform dialog: the page under it stays
 * composed with its scroll and focus, Back pops it, and focus returns to the card that opened
 * it, as a tvOS navigation stack does.
 */
@Composable
fun PageStack(content: @Composable () -> Unit) {
    val stack = remember { mutableStateListOf<Page>() }
    val navigator = remember {
        Navigator(push = { stack.add(it) }, pop = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }, reset = { stack.clear() })
    }
    CompositionLocalProvider(LocalNavigator provides navigator) {
        content()
        // "Add to List" opens from the top window: a dialog under a page's window never gets key focus.
        if (stack.isEmpty()) PickingHost()
        stack.forEachIndexed { index, page ->
            Dialog(
                // Add to List… opens a frame or more after its menu closes (longer on a slow Fire TV); a
                // Back in that gap reaches this page's window, and cancels the picker rather than
                // popping the page under it.
                onDismissRequest = {
                    if (CustomListsStore.picking != null) CustomListsStore.picking = null
                    else if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                when (page) {
                    is Page.Details -> DetailsScreen(page.target, page.openWatchState, page.autoplay)
                    is Page.Grid -> ListGridScreen(page.list, page.query)
                    is Page.Person -> PersonScreen(page.person)
                    Page.HomeLayout -> HomeLayoutSettings()
                    Page.TopMenu -> TopMenuSettings()
                    Page.SubtitleStyle -> SubtitleSettings()
                    Page.Recordings -> RecordingsScreen(asPage = true)
                }
                if (index == stack.lastIndex) PickingHost()
            }
        }
    }
}

@Composable
private fun PickingHost() {
    CustomListsStore.picking?.let { AddToListDialog(it) }
    UpdatePrompt()
    AppToast.Host()
}
