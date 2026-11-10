package com.strmr.tv.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.TrackProbe
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.api.probeTracks
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.EffectiveTracks
import com.strmr.tv.player.ManualSourceTracks
import com.strmr.tv.player.ManualTrackChoice
import com.strmr.tv.player.TrackOverrides
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manual selection's per-visit track state (the Expo app's `healthChecks` track details and
 * `trackOverrides`): each long-pressed result's probe, the tracks chosen for it, and which
 * result's panel is open. Results are keyed by their position in the search response.
 */
class SourceTracks(private val scope: CoroutineScope) {
    private val probes = mutableStateMapOf<Int, ProbeState>()
    private val choices = mutableStateMapOf<Int, ManualTrackChoice>()
    private var open by mutableStateOf<SourceResult?>(null)
    private var defaults by mutableStateOf<TrackDefaults?>(null)

    fun hasChoice(result: SourceResult) = choices[result.index]?.isEmpty == false

    /** Opens the result's panel, probing it unless an earlier probe this visit found its tracks. */
    fun open(result: SourceResult) {
        open = result
        AppLog.i(TAG, "long press ${result.serviceType} \"${result.title}\"")
        if (defaults == null) scope.launch {
            val playback = runCatching { ApiClient.playbackSettings() }.getOrNull()
            val effective = EffectiveTracks.resolve(null, playback, TrackOverrides.audioLanguage, TrackOverrides.subtitleLanguage,
                                                    TrackOverrides.subtitleMode, TrackOverrides.losslessAudio)
            defaults = TrackDefaults(effective.audioLanguage, effective.subtitleLanguage, effective.subtitleMode, effective.allowedLanguages,
                                     effective.losslessAudio)
        }
        val known = probes[result.index]
        if (known == null || known.probe is TrackProbe.Failed) probe(result)
    }

    private fun probe(result: SourceResult) {
        probes[result.index] = ProbeState(null)
        scope.launch {
            val probe = try {
                ApiClient.probeTracks(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TrackProbe.Failed(e.message ?: "Request failed")
            }
            when (probe) {
                is TrackProbe.Tracks -> AppLog.i(TAG, "probed \"${result.title}\": audio=${probe.audio.map { "${it.index}:${it.language}/${it.codec}" }} " +
                    "subtitles=${probe.subtitles.map { "${it.index}:${it.language}/${it.codec}${if (it.forced) "/forced" else ""}" }}")
                is TrackProbe.Failed -> AppLog.w(TAG, "probe failed for \"${result.title}\": ${probe.reason}")
            }
            probes[result.index] = ProbeState(probe)
        }
    }

    /** The hand-picked tracks for the result with the lists they number, or null to play as before. */
    fun manualFor(result: SourceResult): ManualSourceTracks? {
        val choice = choices[result.index]?.takeIf { !it.isEmpty } ?: return null
        val tracks = probes[result.index]?.probe as? TrackProbe.Tracks ?: return null
        return ManualSourceTracks(choice, tracks.audio, tracks.subtitles)
    }

    /** The result whose track panel is up, or null. */
    val openResult: SourceResult? get() = open

    /** The panel's release line: the scraper's raw title's first line, else the title. */
    fun release(result: SourceResult): String =
        (result.attributes["raw_title"] ?: result.title).lineSequence().firstOrNull() ?: result.title

    fun probeState(result: SourceResult): ProbeState = probes[result.index] ?: ProbeState(null)

    /** The profile's track defaults, once read (the panel preselects from them). */
    val trackDefaults: TrackDefaults? get() = defaults

    fun choice(result: SourceResult): ManualTrackChoice = choices[result.index] ?: ManualTrackChoice()

    fun choose(result: SourceResult, choice: ManualTrackChoice) {
        choices[result.index] = choice
    }

    fun retry(result: SourceResult) = probe(result)

    fun close() {
        open = null
    }

    private companion object {
        const val TAG = "ManualTracks"
    }
}
