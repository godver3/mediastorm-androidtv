package com.strmr.tv.ui.live.multiscreen

import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.MultiscreenMemory
import com.strmr.tv.live.MultiscreenPrefs
import com.strmr.tv.live.MultiscreenSession
import com.strmr.tv.live.SavedMultiscreen
import com.strmr.tv.live.liveUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Resume multiscreen for [MultiscreenModel]: the channels on show saved for the profile as they
 * change, and a saved set ([saved]) reopened, as Expo's restore / restoreExtras: the first two
 * screens at once, then each extra after a capacity check (behind the third-screen warning,
 * [warn]). Nothing is saved until the screens are back, so screens a stream limit left out don't
 * overwrite the saved set; [stopped] gets why extras were left out. Main thread only.
 */
internal class MultiscreenResume(
    private val session: MultiscreenSession,
    private val scope: CoroutineScope,
    private val selections: MultiscreenSelections,
    private val screenLimit: () -> Int,
    private val warn: (onContinue: () -> Unit, onCancel: () -> Unit) -> Unit,
    private val stopped: (String) -> Unit,
    saved: SavedMultiscreen?,
) {
    /** Reopening a saved multiscreen: nothing is saved until its screens are back. */
    private var restoring = saved != null
    private var lastSaved: SavedMultiscreen? = null
    private val profileId = ApiClient.profileId

    private val state get() = session.state.value
    private val occupied get() = state.slots.count { it.isOccupied }

    init {
        if (saved != null) {
            AppLog.i("Multiscreen", "resuming ${saved.channels.size} screens")
            scope.launch { restore(saved) }
        }
        scope.launch {
            session.state.collect { s ->
                val selection = MultiscreenMemory.selection(s)
                if (restoring || selection == null || selection == lastSaved) return@collect
                lastSaved = selection
                MultiscreenPrefs.save(profileId, selection)
            }
        }
    }

    private suspend fun restore(saved: SavedMultiscreen) {
        val slotOf = mutableMapOf<Int, Int>()
        fun listenIfAudible(position: Int) {
            val slot = slotOf[position] ?: return
            if (position == saved.audioIndex) {
                session.listen(slot)
                session.focus(slot)
            }
        }
        saved.channels.take(2).mapIndexed { i, channel -> slotOf[i] = i; i to session.tune(i, channel) }
            .forEach { (i, tune) -> tune.await(); listenIfAudible(i) }
        val extras = saved.channels.withIndex().drop(2)
        val addExtras: () -> Unit = { scope.launch { restoreExtras(extras, slotOf, ::listenIfAudible) } }
        when {
            extras.isEmpty() -> endRestore()
            MultiscreenMemory.needsWarning(occupied, MultiscreenPrefs.warningShown) ->
                warn(addExtras, ::endRestore)
            else -> addExtras()
        }
    }

    private suspend fun restoreExtras(extras: List<IndexedValue<LiveChannel>>, slotOf: MutableMap<Int, Int>, listen: (Int) -> Unit) {
        val generation = selections.begin()
        try {
            for ((position, channel) in extras) {
                if (session.indexOf(channel) != -1) continue
                if (occupied >= screenLimit()) break
                val usage = ApiClient.liveUsage(channel.sourceId)
                if (!selections.isCurrent(generation)) return
                if (!usage.hasCapacity) return stopped("Stream limit reached. Remaining screens were not added.")
                slotOf[position] = state.slots.size
                session.tune(state.slots.size, channel).await()
                listen(position)
                if (!selections.isCurrent(generation)) return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Multiscreen", "resume capacity check failed: ${e.message}")
            if (selections.isCurrent(generation)) stopped("Unable to check stream capacity. Remaining screens were not added.")
        } finally {
            selections.end(generation)
            endRestore()
        }
    }

    /** From here, changes are saved again (screens a stream limit left out don't overwrite the saved set). */
    private fun endRestore() {
        lastSaved = MultiscreenMemory.selection(state)
        restoring = false
    }
}
