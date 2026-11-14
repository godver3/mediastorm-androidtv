package com.strmr.tv.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import com.strmr.tv.api.saveContentAudioLanguage
import kotlinx.coroutines.launch

// Picking audio and subtitle tracks, from the menus and the automatic subtitle search.

/** `byUser`: picked in a menu, which stops a running automatic subtitle search. */
fun PlayerController.select(track: PlayerTrack, byUser: Boolean = true) {
    if (track.group.type == C.TRACK_TYPE_TEXT) {
        if (byUser) online.userChose()
        external.deselect()
    }
    if (byUser && track.group.type == C.TRACK_TYPE_AUDIO) rememberAudioLanguage(track.format.language)
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(track.group.type, false)
        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.index))
        .build()
}

fun PlayerController.subtitlesOff() {
    online.userChose()
    external.deselect()
    disableEmbeddedSubtitles()
}

/** The loaded external track, picked in the Subtitles menu. */
fun PlayerController.selectExternal() {
    online.userChose()
    disableEmbeddedSubtitles()
    external.select()
}

/** A new external track (downloaded or translated), shown in place of the stream's subtitles. */
fun PlayerController.showExternal(track: ExternalTrack) {
    disableEmbeddedSubtitles()
    external.show(track)
}

private fun PlayerController.disableEmbeddedSubtitles() {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    cues.value = emptyList()
}

/** Only an explicit menu choice changes language intent; a decoder fallback does not. */
private fun PlayerController.rememberAudioLanguage(raw: String?) {
    val language = raw?.trim()?.takeIf { it.isNotEmpty() && it != "und" } ?: return
    if (session.isLive || !session.reportsProgress || session.localFile) return
    val previous = audioPreferenceSave
    val context = ServerReleaseReportGateway()
    audioPreferenceSave = scope.launch {
        previous?.join()
        if (!context.isCurrent()) return@launch
        try {
            com.strmr.tv.api.ApiClient.saveContentAudioLanguage(request.title.titleId, if (request.isEpisode) "series" else "movie", language)
            audioPreferenceFailed = false
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            audioPreferenceFailed = true
            notice.value = "Audio changed, but its language could not be saved. Select it again before reporting."
        }
    }
}
