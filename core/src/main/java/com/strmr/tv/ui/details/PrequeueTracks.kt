package com.strmr.tv.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Episode
import com.strmr.tv.api.PlaybackSettings
import com.strmr.tv.api.PrequeueStatus
import com.strmr.tv.api.TrackProbe
import com.strmr.tv.player.EffectiveTracks
import com.strmr.tv.player.ManualSourceTracks
import com.strmr.tv.player.ManualTrackChoice
import com.strmr.tv.player.ManualTracks
import com.strmr.tv.player.PrequeuePick
import com.strmr.tv.player.WarmPrequeue
import com.strmr.tv.player.trackLists

/** A result's track probe for the panel: null while it runs. */
data class ProbeState(val probe: TrackProbe?)

/** What the panel preselects before the user changes anything: the profile's languages and mode. */
data class TrackDefaults(
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    val subtitleMode: String?,
    val allowed: List<String>,
    val losslessAudio: Boolean = false,
)

/** Tracks the server already chose (container stream numbers; a null subtitle is off), preselected instead. */
data class PreselectedTracks(val audio: Int?, val subtitle: Int?)

/**
 * Details › Audio & Subtitles: picks for the stream the page's prequeue resolved, kept for the
 * visit by its stream path and cleared after playback.
 */
class PrequeueTracks(
    val model: DetailsModel,
    /** The episode Play targets: the up-next one, unless the page lets another be selected (phones). */
    private val episode: () -> Episode? = { model.primaryEpisode },
) {
    private var choice by mutableStateOf(ManualTrackChoice())
    private var choicePath by mutableStateOf<String?>(null)
    var open by mutableStateOf(false)
    var playback by mutableStateOf<PlaybackSettings?>(null)

    /** The page's prequeue while it resolves, or once it lists tracks for the episode Play targets; null hides the button. */
    fun visible(warm: WarmPrequeue?): WarmPrequeue? {
        if (model.primaryVersions.isNotEmpty()) return null // Play uses a library copy
        if (warm == null || warm.titleId != model.target.titleId || warm.profile != ApiClient.profileId) return null
        return when (warm.phase) {
            WarmPrequeue.Phase.RESOLVING -> warm
            WarmPrequeue.Phase.UNAVAILABLE -> null
            WarmPrequeue.Phase.READY -> {
                val status = warm.tracks ?: return null
                if (!model.target.isMovie) {
                    val ep = episode() ?: return null
                    val target = status.targetEpisode
                    if (target?.seasonNumber != ep.seasonNumber || target.episodeNumber != ep.episodeNumber) return null
                }
                warm
            }
        }
    }

    fun choiceFor(status: PrequeueStatus): ManualTrackChoice = if (choicePath == status.streamPath) choice else ManualTrackChoice()

    fun choose(status: PrequeueStatus, new: ManualTrackChoice) {
        choicePath = status.streamPath
        choice = new
    }

    fun clear() {
        choicePath = null
        choice = ManualTrackChoice()
    }

    /** The picks to hand to Play, bound to the prequeued stream. */
    fun pick(warm: WarmPrequeue?): PrequeuePick? {
        val status = visible(warm)?.tracks ?: return null
        val path = status.streamPath ?: return null
        val c = choiceFor(status).takeIf { !it.isEmpty } ?: return null
        val lists = status.trackLists
        return PrequeuePick(path, status.targetEpisode, ManualSourceTracks(c, lists.audio, lists.subtitles))
    }

    /** The profile's languages after this device's overrides, for the panel's allowed-language filter. */
    fun defaults(status: PrequeueStatus): TrackDefaults {
        val e = EffectiveTracks.current(status.trackSelection, playback)
        return TrackDefaults(e.audioLanguage, e.subtitleLanguage, e.subtitleMode, e.allowedLanguages, e.losslessAudio)
    }

    /**
     * What Play picks: the server's selection, unless this device's overrides change it (the
     * player then picks by language, as the panel's own defaults do).
     */
    fun preselected(status: PrequeueStatus): PreselectedTracks? {
        val selection = status.trackSelection ?: return null
        val e = EffectiveTracks.current(selection, playback)
        if (e.clientAudio || e.clientSubtitles) return null
        val subtitle = if (selection.subtitleMode == "off") null else (selection.subtitle ?: selection.textSubtitle)?.streamIndex
        return PreselectedTracks(selection.audio?.streamIndex, subtitle)
    }

    /** "English · EAC3 · Subtitles Off", for the picks or else what Play would pick. */
    fun caption(status: PrequeueStatus): String {
        val lists = status.trackLists
        val chosen = choiceFor(status)
        val pre = preselected(status)
        val d = defaults(status)
        val audioIndex = chosen.audio ?: pre?.audio ?: ManualTracks.defaultAudio(lists.audio, d.audioLanguage, d.losslessAudio)
        val subtitleIndex = when (chosen.subtitle) {
            null -> if (pre != null) pre.subtitle else ManualTracks.defaultSubtitle(
                lists.subtitles, d.subtitleLanguage, d.subtitleMode,
                (lists.audio.firstOrNull { it.index == audioIndex } ?: lists.audio.firstOrNull())?.language,
            )
            ManualTrackChoice.OFF -> null
            else -> chosen.subtitle
        }
        val parts = mutableListOf<String>()
        (lists.audio.firstOrNull { it.index == audioIndex } ?: lists.audio.firstOrNull())?.let { a ->
            parts += ManualTracks.languageName(a.language)
            a.codec.ifBlank { null }?.let { parts += it.uppercase() }
        }
        parts += lists.subtitles.firstOrNull { it.index == subtitleIndex }?.let { "${ManualTracks.languageName(it.language)} Subtitles" }
            ?: "Subtitles Off"
        return parts.joinToString(" · ")
    }
}
