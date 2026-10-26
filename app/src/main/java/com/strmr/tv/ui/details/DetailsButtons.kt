package com.strmr.tv.ui.details

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.App
import com.strmr.tv.ui.SidePanelDialog

/** The details page's secondary actions that Customize Buttons can move to More, in row order. */
enum class DetailsAction(val title: String) {
    START_OVER("Start Over"),
    VERSIONS("Versions"),
    CHOOSE_SOURCE("Choose Source"),
    TRACKS("Audio & Subtitles"),
    TRAILER("Trailer"),
    WATCHLIST("Watchlist"),
    LISTS("Manage Lists"),
    WATCHED("Watch State"),
}

/**
 * Which of the details page's secondary actions sit in the action row and which are listed in
 * More instead (Customize Buttons…, at the end of More). Play / Resume and More always stay in
 * the row, in a fixed order, so a hidden button only closes its gap. Kept on the device, per
 * profile; Manage Lists starts in More (as on tvOS).
 */
object DetailsButtons {
    val defaultInMore = setOf(DetailsAction.LISTS)

    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private fun key(profileId: String) = "detailsButtons.inMore.$profileId"

    /** Bumped on every change, so the header and More recompose. */
    private var revision by mutableStateOf(0)

    fun inMore(profileId: String = ApiClient.profileId): Set<DetailsAction> {
        revision
        return decode(if (prefs.contains(key(profileId))) prefs.getStringSet(key(profileId), emptySet()) else null)
    }

    fun inRow(action: DetailsAction): Boolean = action !in inMore()

    fun setInRow(action: DetailsAction, shown: Boolean, profileId: String = ApiClient.profileId) {
        val more = inMore(profileId).let { if (shown) it - action else it + action }
        prefs.edit().putStringSet(key(profileId), more.map { it.name }.toSet()).apply()
        revision++
    }

    /** The stored names, else (nothing stored) the defaults; unknown names are dropped. */
    internal fun decode(stored: Set<String>?): Set<DetailsAction> =
        stored?.mapNotNull { name -> DetailsAction.entries.firstOrNull { it.name == name } }?.toSet() ?: defaultInMore
}

/**
 * Customize Buttons: one row per secondary action showing where it goes, In row or In More;
 * selecting it moves it to the other, with the panel left up.
 */
@Composable
fun DetailsButtonsDialog(onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    SidePanelDialog("Customize Buttons", "Where each button goes", onDismiss) {
        itemsIndexed(DetailsAction.entries) { index, action ->
            val shown = DetailsButtons.inRow(action)
            ListItem(
                selected = false,
                onClick = { DetailsButtons.setInRow(action, !shown) },
                headlineContent = { Text(action.title) },
                trailingContent = { Text(if (shown) "In row" else "In More") },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
        item {
            Text(
                "Select a button to move it between the row on the details page and the More menu. Play and More always stay in the row. Saved on this device for this profile.",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
