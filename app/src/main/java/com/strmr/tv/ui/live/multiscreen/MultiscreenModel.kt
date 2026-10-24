package com.strmr.tv.ui.live.multiscreen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MimeTypes
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.EpgNowPlaying
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStore
import com.strmr.tv.live.LiveStream
import com.strmr.tv.live.MultiscreenMemory
import com.strmr.tv.live.MultiscreenPrefs
import com.strmr.tv.live.MultiscreenSession
import com.strmr.tv.live.SavedMultiscreen
import com.strmr.tv.live.epgNow
import com.strmr.tv.live.keepLiveSessionAlive
import com.strmr.tv.live.liveUsage
import com.strmr.tv.live.startLive
import com.strmr.tv.live.stopLiveSessionStrict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The multiscreen screen's state around its [MultiscreenSession], a port of the Expo TV
 * `TVMultiscreen`: the enlarged tile, the control bar, the add / change channel picker, the
 * stability warning before a third screen (once per device), the stream-limit replacement
 * prompt and the exit that releases every stream. The channels on show are remembered for
 * Resume multiscreen ([resume] reopens them). Main thread only.
 */
@Stable
class MultiscreenModel(handover: MultiscreenHandover?, resume: SavedMultiscreen?, private val finish: () -> Unit) {
    /** Outlives the screen, so the sessions are still released when the activity goes first. */
    private val sessionScope = MainScope()
    private val scope = MainScope()

    private val transport = object : MultiscreenSession.Transport {
        override suspend fun start(channel: LiveChannel): LiveStream {
            if (!ApiClient.liveUsage(channel.sourceId).hasCapacity) throw IllegalStateException("Provider connection limit reached")
            return ApiClient.startLive(channel, channel.name, forceHls = true).also {
                AppLog.i("Multiscreen", "tuned ${channel.name} session=${it.sessionId ?: "direct"}")
            }
        }

        override suspend fun stop(sessionId: String) = ApiClient.stopLiveSessionStrict(sessionId)
    }
    val session = MultiscreenSession(transport, sessionScope)
    /** The picker's channels and filters, kept between picks. */
    val store = LiveStore(scope)

    var expanded by mutableStateOf<Int?>(null)
    var actions by mutableStateOf(false)
    var warning by mutableStateOf(false)
    var replacement by mutableStateOf<LiveChannel?>(null)
    /** Why Add Screen can't add one (the screen cap or this box's decoders). */
    var notice by mutableStateOf<String?>(null)
    var picker by mutableStateOf(false)
    /** The picker adds a screen (rather than changing the focused one's channel). */
    private var adding by mutableStateOf(false)
    /** Not yet showing two channels: closing the picker leaves multiscreen (from the Live tab it chooses both). */
    private var initialSetup by mutableStateOf(false)
    /** Opened from the player, whose channel is screen 1: the picker adds the second. */
    private val fromPlayer = handover != null
    var pickerError by mutableStateOf<String?>(null)
    var exitError by mutableStateOf<String?>(null)
    var exiting by mutableStateOf(false)
    var lastInput by mutableLongStateOf(System.currentTimeMillis())
    /** Now / next by guide key, for the tiles' channels. */
    var epg by mutableStateOf<Map<String, EpgNowPlaying>>(emptyMap())
    /** What each tile's player reports (its video type feeds [DecoderLimits]). */
    val streamInfo = mutableStateMapOf<Int, TileStreamInfo>()
    /** What the warning's Continue / Cancel go on to. */
    private var afterWarning: Pair<() -> Unit, () -> Unit>? = null
    /** Reopening a saved multiscreen: nothing is saved until its screens are back. */
    private var restoring = resume != null
    private var lastSaved: SavedMultiscreen? = null
    private val profileId = ApiClient.profileId
    private var selecting = false
    private var selectionRequest = 0

    private val state get() = session.state.value
    private val occupied get() = state.slots.indices.filter { state.slots[it].isOccupied }

    init {
        // Logged for the record: what this box says it can decode at once.
        AppLog.i("Multiscreen", "device screen limit: h264=${DecoderLimits.instances(MimeTypes.VIDEO_H264)} " +
            "hevc=${DecoderLimits.instances(MimeTypes.VIDEO_H265)}")
        if (handover != null) {
            if (handover.stream != null) session.adopt(handover.channel, handover.stream) else session.tune(0, handover.channel)
            AppLog.i("Multiscreen", "from the player: ${handover.channel.name} (${if (handover.stream != null) "session kept" else "retuned"})")
            session.focus(1)
            adding = true
            initialSetup = true
            picker = true
        } else if (resume != null) {
            AppLog.i("Multiscreen", "resuming ${resume.channels.size} screens")
            scope.launch { restore(resume) }
        } else {
            adding = true
            initialSetup = true
            picker = true
        }
        scope.launch {
            session.state.collect { s ->
                val selection = MultiscreenMemory.selection(s)
                if (restoring || selection == null || selection == lastSaved) return@collect
                lastSaved = selection
                MultiscreenPrefs.save(profileId, selection)
            }
        }
        scope.launch {
            while (isActive) {
                delay(KEEPALIVE_MS)
                state.slots.mapNotNull { it.sessionId }.forEach { ApiClient.keepLiveSessionAlive(it) }
            }
        }
    }

    /** The picker's title, for the screen `focused`. */
    fun pickerTitle(focused: Int): String = when {
        adding && (fromPlayer || !initialSetup) -> "Add channel"
        adding -> if (focused == 0) "Choose first channel" else "Choose second channel"
        else -> "Choose channel · Screen ${focused + 1}"
    }

    /** At most four screens, fewer when this box can't decode that many videos at once. */
    val screenLimit: Int
        get() = minOf(MultiscreenSession.MAX_SCREENS, DecoderLimits.screens(streamInfo.values.mapNotNull { it.videoMime }.toSet()))

    fun reveal() {
        lastInput = System.currentTimeMillis()
    }

    suspend fun refreshEpg(keys: List<String>) {
        if (keys.isEmpty()) return
        runCatching { ApiClient.epgNow(keys) }.onSuccess { list -> epg = epg + list.associateBy { it.channelId } }
    }

    // Remote

    /** Select on a tile: the controls for one not playing, else listen to it, else enlarge it. */
    fun select(index: Int) {
        reveal()
        session.focus(index)
        val slot = state.slots.getOrNull(index) ?: return
        when {
            slot.source == null || slot.error != null || slot.loading -> actions = true
            state.audioIndex != index -> session.listen(index)
            else -> expanded = index
        }
    }

    /** An arrow over the tiles; Down with no tile below opens the control bar. */
    fun arrow(direction: TileDirection, frames: Map<Int, TileFrame>) {
        reveal()
        val next = MultiscreenLayout.neighbor(frames, state.focusedIndex, direction)
        if (next != null) session.focus(next) else if (direction == TileDirection.DOWN) actions = true
    }

    /** Back: the control bar, then the enlarged tile, then out of multiscreen. Dialogs take their own. */
    fun back() {
        reveal()
        when {
            actions -> actions = false
            expanded != null -> expanded = null
            else -> exit()
        }
    }

    // Control bar

    fun addScreen() {
        actions = false
        val limit = screenLimit
        when {
            occupied.size >= limit -> notice = if (limit >= MultiscreenSession.MAX_SCREENS) {
                "Multiscreen shows up to ${MultiscreenSession.MAX_SCREENS} screens. Remove one to add another."
            } else {
                "This device can decode $limit videos at once. Remove a screen to add another."
            }
            MultiscreenMemory.needsWarning(occupied.size, MultiscreenPrefs.warningShown) ->
                showWarning(onContinue = ::beginAdd, onCancel = { actions = true })
            else -> beginAdd()
        }
    }

    /** The warning is never shown again on this device, from the moment it first shows. */
    private fun showWarning(onContinue: () -> Unit, onCancel: () -> Unit) {
        MultiscreenPrefs.markWarningShown()
        afterWarning = onContinue to onCancel
        warning = true
    }

    fun acceptWarning() {
        warning = false
        afterWarning?.first?.invoke()
        afterWarning = null
    }

    /**
     * Cancel or Back on the warning: back to the control bar (on resume, the extra screens aren't
     * added). The dialog dismisses before any choice's action, so this waits a turn: Continue
     * ([acceptWarning]) has taken the continuation by then.
     */
    fun dismissWarning() {
        warning = false
        scope.launch {
            afterWarning?.second?.invoke()
            afterWarning = null
        }
    }

    private fun beginAdd() {
        adding = true
        actions = false
        pickerError = null
        picker = true
    }

    fun changeChannel() {
        actions = false
        adding = false
        pickerError = null
        picker = true
    }

    fun retry() {
        val index = state.focusedIndex
        state.slots[index].channel?.let { session.tune(index, it) }
        actions = false
    }

    fun remove() {
        val index = state.focusedIndex
        actions = false
        scope.launch {
            session.remove(index).await()
            if (state.slots[index].channel != null) return@launch
            val remaining = maxOf(0, state.slots.indexOfFirst { it.channel != null })
            session.focus(remaining)
            expanded = remaining.takeIf { state.slots.count { it.channel != null } == 1 && state.slots[remaining].source != null }
        }
    }

    /** The control bar's Enlarge: offered for the focused screen while it plays. */
    fun canEnlarge(state: MultiscreenSession.State): Boolean {
        val slot = state.slots.getOrNull(state.focusedIndex) ?: return false
        return slot.source != null && slot.error == null && !slot.loading
    }

    /** Enlarge (the focused screen) or, with one enlarged, Equal tiles. */
    fun toggleEnlarged() {
        expanded = if (expanded != null) null else state.focusedIndex.takeIf { canEnlarge(state) }
        actions = false
    }

    fun replace(index: Int, channel: LiveChannel) {
        session.tune(index, channel)
        session.focus(index)
        replacement = null
        expanded = null
    }

    // Picker

    fun closePicker() {
        ++selectionRequest
        selecting = false
        picker = false
        reveal()
        if (initialSetup && adding && state.slots[1].channel == null) exit() else adding = false
    }

    fun choose(channel: LiveChannel) {
        if (selecting) return
        val duplicate = session.indexOf(channel)
        if (duplicate != -1) {
            if (initialSetup && adding && state.slots[1].channel == null) return
            session.focus(duplicate)
            adding = false
            picker = false
            return
        }
        val empty = state.slots.indexOfFirst { !it.isOccupied }
        val target = if (adding) (if (empty == -1) state.slots.size else empty) else state.focusedIndex
        val generation = ++selectionRequest
        selecting = true
        scope.launch {
            try {
                // Checked before a screen is added; a refused or failed check leaves the others alone.
                if (adding) {
                    val usage = ApiClient.liveUsage(channel.sourceId)
                    if (generation != selectionRequest) return@launch
                    if (!usage.hasCapacity) {
                        replacement = channel
                        adding = false
                        picker = false
                        return@launch
                    }
                }
                session.tune(target, channel)
                session.focus(target)
                if (initialSetup && adding && target == 0 && state.slots[1].channel == null) {
                    session.focus(1)
                } else {
                    initialSetup = false
                    reveal()
                    adding = false
                    expanded = null
                    picker = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Multiscreen", "capacity check failed: ${e.message}")
                if (generation == selectionRequest) pickerError = "Unable to check stream capacity. Retry before adding a screen."
            } finally {
                if (generation == selectionRequest) selecting = false
            }
        }
    }

    // Resume

    /** The saved screens: the first two at once, then each extra after a capacity check, as Expo's restoreExtras. */
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
            MultiscreenMemory.needsWarning(occupied.size, MultiscreenPrefs.warningShown) ->
                showWarning(onContinue = addExtras, onCancel = ::endRestore)
            else -> addExtras()
        }
    }

    private suspend fun restoreExtras(extras: List<IndexedValue<LiveChannel>>, slotOf: MutableMap<Int, Int>, listen: (Int) -> Unit) {
        val generation = ++selectionRequest
        selecting = true
        try {
            for ((position, channel) in extras) {
                if (session.indexOf(channel) != -1) continue
                if (occupied.size >= screenLimit) break
                val usage = ApiClient.liveUsage(channel.sourceId)
                if (generation != selectionRequest) return
                if (!usage.hasCapacity) {
                    exitError = "Stream limit reached. Remaining screens were not added."
                    actions = true
                    return
                }
                slotOf[position] = state.slots.size
                session.tune(state.slots.size, channel).await()
                listen(position)
                if (generation != selectionRequest) return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Multiscreen", "resume capacity check failed: ${e.message}")
            if (generation == selectionRequest) {
                exitError = "Unable to check stream capacity. Remaining screens were not added."
                actions = true
            }
        } finally {
            if (generation == selectionRequest) selecting = false
            endRestore()
        }
    }

    /** From here, changes are saved again (screens a stream limit left out don't overwrite the saved set). */
    private fun endRestore() {
        lastSaved = MultiscreenMemory.selection(state)
        restoring = false
    }

    // Exit

    /** Releases every stream and closes; a failed release stays, with the reason in the control bar. */
    fun exit() {
        if (exiting) return
        ++selectionRequest
        selecting = false
        exiting = true
        scope.launch {
            try {
                session.close()
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Multiscreen", "exit: ${e.message}")
                exitError = "Unable to release all streams. Select Exit to retry."
                actions = true
            } finally {
                exiting = false
            }
        }
    }

    /** The activity is going (Back, Home, sleep): release whatever is still open, then let go. */
    fun dispose() {
        scope.cancel()
        sessionScope.launch {
            withContext(NonCancellable) {
                runCatching { session.close() }.onFailure { AppLog.w("Multiscreen", "cleanup: ${it.message}") }
            }
            sessionScope.cancel()
        }
    }

    private companion object {
        const val KEEPALIVE_MS = 10_000L
    }
}
