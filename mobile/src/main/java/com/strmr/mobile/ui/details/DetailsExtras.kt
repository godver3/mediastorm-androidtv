package com.strmr.mobile.ui.details

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import com.strmr.mobile.ui.components.SheetAction
import com.strmr.tv.api.Episode
import com.strmr.tv.ui.details.DetailsModel

/**
 * What a details page hands to the features that plug into it: the page's model, the episode
 * Play targets (null for a movie), and Play itself (what the primary button does, library copy
 * or stream, with the page's track picks).
 */
class DetailsActionContext(
    val model: DetailsModel,
    val episode: Episode?,
    val playPrimary: () -> Unit,
)

/**
 * Actions other features add to the details page without the page knowing them, Downloads
 * and Cast (as the iPhone page's Download and Cast to Device…). Each slot is optional; the
 * page draws whatever [DetailsExtras.provider] gives, in these places:
 */
interface DetailsExtraActions {
    /** Round actions at the end of the action row (a Download button), as [DetailsActionItem]s. */
    @Composable
    fun RowScope.Actions(context: DetailsActionContext) {}

    /** Top app bar icons before More (a Cast button). */
    @Composable
    fun RowScope.TopBar(context: DetailsActionContext) {}

    /** Rows added to the page's More sheet (Cast to Device…, Download Season…). */
    fun moreActions(context: DetailsActionContext): List<SheetAction> = emptyList()

    /** Rows added to an episode's long-press sheet (Download Episode). */
    fun episodeActions(context: DetailsActionContext, episode: Episode): List<SheetAction> = emptyList()
}

/** Where the app registers its [DetailsExtraActions] (once, at start-up); null adds nothing. */
object DetailsExtras {
    @Volatile
    var provider: DetailsExtraActions? = null
}
