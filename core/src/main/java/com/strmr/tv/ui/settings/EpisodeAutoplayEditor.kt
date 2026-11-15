package com.strmr.tv.ui.settings

import com.strmr.tv.api.ApiClient
import com.strmr.tv.library.ProfileDataContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable private data class AutoplayFields(val nextEpisodeAutoPlay: Boolean? = null)
@Serializable private data class AutoplaySettings(val playback: AutoplayFields? = null)
@Serializable private data class AutoplayReply(val nextEpisode: Boolean? = null)

interface EpisodeAutoplayGateway {
    fun isCurrent(): Boolean
    suspend fun read(): Boolean
    suspend fun write(value: Boolean): Boolean
}

class ServerEpisodeAutoplayGateway : EpisodeAutoplayGateway {
    private val context = ProfileDataContext.current
    private val path = ApiClient.profilePath
    override fun isCurrent() = context == ProfileDataContext.current
    private fun checkCurrent() { if (!isCurrent()) throw CancellationException("Playback profile changed") }
    override suspend fun read(): Boolean {
        checkCurrent()
        val reply = ApiClient.send<AutoplaySettings>("GET", "$path/settings", timeoutMs = 5_000)
        checkCurrent()
        checkNotNull(reply.playback) { "Missing profile playback settings" }
        return reply.playback.nextEpisodeAutoPlay ?: true
    }
    override suspend fun write(value: Boolean): Boolean {
        checkCurrent()
        val reply = ApiClient.send<AutoplayReply>("PATCH", "$path/settings/skipping", body = buildJsonObject { put("nextEpisode", value) }, timeoutMs = 5_000)
        checkCurrent()
        return checkNotNull(reply.nextEpisode) { "Missing playback confirmation" }
    }
}

data class EpisodeAutoplayChange(val context: Long, val enabled: Boolean, val revision: Long)

object EpisodeAutoplayChanges {
    private var revision = 0L
    private val _changes = MutableStateFlow<EpisodeAutoplayChange?>(null)
    val changes: StateFlow<EpisodeAutoplayChange?> = _changes.asStateFlow()
    fun saved(value: Boolean) { _changes.value = EpisodeAutoplayChange(ProfileDataContext.current, value, ++revision) }
}

data class EpisodeAutoplayState(val enabled: Boolean? = null, val loading: Boolean = true, val saving: Boolean = false, val error: String? = null) {
    val description: String get() = error ?: when {
        loading -> "Loading profile choice…"
        saving -> "Saving profile choice…"
        else -> "${if (enabled == true) "On" else "Off"} · saved for this profile on all devices"
    }
}

/** A confirmed profile setting, with retry after a lost acknowledgement. */
class EpisodeAutoplayEditor(private val gateway: EpisodeAutoplayGateway, private val onSaved: (Boolean) -> Unit = EpisodeAutoplayChanges::saved) {
    private val _state = MutableStateFlow(EpisodeAutoplayState())
    val state: StateFlow<EpisodeAutoplayState> = _state.asStateFlow()
    private var closed = false
    private var busy = false
    fun close() { closed = true }
    private fun checkCurrent() { if (closed || !gateway.isCurrent()) throw CancellationException("Playback profile changed") }
    suspend fun load() {
        if (busy) return
        checkCurrent(); busy = true
        _state.value = EpisodeAutoplayState()
        try {
            val value = gateway.read(); checkCurrent()
            _state.value = EpisodeAutoplayState(enabled = value, loading = false)
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) {
            checkCurrent(); _state.value = EpisodeAutoplayState(loading = false, error = "Could not load this profile's choice. Select to retry.")
        } finally { busy = false }
    }
    suspend fun save(value: Boolean) {
        if (busy || _state.value.enabled == null) return
        checkCurrent(); busy = true
        val previous = _state.value.enabled
        _state.value = EpisodeAutoplayState(enabled = previous, loading = false, saving = true)
        try {
            val confirmed = gateway.write(value); checkCurrent()
            check(confirmed == value) { "Unexpected playback confirmation" }
            _state.value = EpisodeAutoplayState(enabled = value, loading = false)
            onSaved(value)
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) {
            checkCurrent(); _state.value = EpisodeAutoplayState(enabled = previous, loading = false, error = "Could not save this profile's choice. Select to retry.")
        } finally { busy = false }
    }
}
