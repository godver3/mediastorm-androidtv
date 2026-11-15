/* Hallmark · Ecosystem Index · existing Mediastorm theme · native Material tokens
 * Audience: family profiles; use: resume and saved titles; tone: quiet cinematic utility.
 * No new motion, fonts, palette or navigation framework.
 */
package com.strmr.mobile.ui.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.app.AppSession
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.ListsTabStore
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.ratings.TitleRatingChanges
import com.strmr.tv.ui.lists.PersonalArea
import com.strmr.tv.ui.lists.refreshPersonalArea
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The personal entry uses the existing list grids and their normal title actions. */
@Composable
fun PersonalAreaScreen(asPage: Boolean) {
    val profile by AppSession.profile.collectAsState()
    if (!PersonalArea.available(profile)) {
        PageScaffold("My Area", showBack = asPage) { padding ->
            Text("Open Home to browse this profile's allowed titles.", modifier = Modifier.padding(padding).padding(16.dp))
        }
        return
    }
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val preferenceRevision by TitleRatingChanges.generation.collectAsState()
    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        try { refreshPersonalArea(); failed = false }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
        finally { refreshing = false }
    }
    LaunchedEffect(navigator.current.isEmpty()) { if (navigator.current.isEmpty()) refresh() }
    val lists = CustomListsStore.lists
    val watching = ListsTabStore.continueWatching
    val count = WatchlistStore.items.size.takeIf { WatchlistStore.isLoaded && WatchlistStore.loadError == null }
    PageScaffold("My Area", showBack = asPage, actions = {
        TextButton(onClick = { scope.launch { refresh() } }) { Text(if (refreshing) "Refreshing…" else "Refresh") }
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item("profile") {
                Text(profile!!.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item("personal") { TileRow("Continue and Saved", PersonalArea.tiles(watching, count, preferenceRevision)) }
            item("your-lists") { TileRow("Your Lists", PersonalArea.ownLists(lists)) { naming = true } }
            if (!refreshing && (failed || lists == null || watching == null || WatchlistStore.loadError != null)) item("retry") {
                Text("Some saved items could not be loaded. Select Refresh to try again.", modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
    if (naming) ListNameDialog("New List", "Add titles from a title's menu.", confirm = "Create", onDismiss = { naming = false }) { name ->
        CustomListsStore.create(name)
    }
}
