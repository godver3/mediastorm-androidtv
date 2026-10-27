package com.strmr.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import com.strmr.tv.api.PlaybackSettings
import com.strmr.tv.api.TrackSelection

/**
 * One playback's track preferences after this device's [TrackOverrides]: the server's
 * (its track choice, else the profile's playback settings) where an override is Default.
 * `clientAudio` / `clientSubtitles` say the override changes that choice, so the player
 * picks the track itself ([ClientTrackSelector]) instead of mapping the server's. Audio Format
 * Lossless re-picks the audio in the same languages, TrueHD first (`losslessAudio`).
 */
data class EffectiveTracks(
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    /** off | forced-only | on */
    val subtitleMode: String?,
    val allowedLanguages: List<String>,
    val clientAudio: Boolean,
    val clientSubtitles: Boolean,
    val losslessAudio: Boolean = false,
    /** Lossless alone changed the audio: with no track to pick by language, keep the server's. */
    val serverAudioFallback: Boolean = false,
) {
    companion object {
        fun resolve(
            selection: TrackSelection?,
            playback: PlaybackSettings?,
            audioOverride: String?,
            subtitleLanguageOverride: String?,
            modeOverride: String?,
            losslessAudio: Boolean = false,
        ): EffectiveTracks {
            val serverAudio = if (selection != null) selection.audioLanguage?.ifEmpty { null } else playback?.preferredAudioLanguage
            val serverLanguage = if (selection != null) selection.subtitleLanguage?.ifEmpty { null } else playback?.preferredSubtitleLanguage
            val serverMode = selection?.subtitleMode?.ifEmpty { null } ?: playback?.preferredSubtitleMode
            fun differs(override: String?, server: String?) =
                override != null && (server == null || !ClientTrackSelector.matchesLanguage(server, "", override))
            // French (Canada) here and plain French on the server (or the reverse) pick different dubs.
            val clientLanguage = differs(audioOverride, serverAudio) ||
                (audioOverride != null && !RegionalAudio.sameVariant(audioOverride, serverAudio))
            // Lossless keeps the audio language, so the subtitle choice stands.
            val clientSubtitles = clientLanguage || differs(subtitleLanguageOverride, serverLanguage) ||
                (modeOverride != null && modeOverride != serverMode)
            return EffectiveTracks(
                audioLanguage = audioOverride ?: serverAudio,
                subtitleLanguage = subtitleLanguageOverride ?: serverLanguage,
                subtitleMode = modeOverride ?: serverMode,
                allowedLanguages = playback?.allowedTrackLanguages.orEmpty(),
                clientAudio = clientLanguage || losslessAudio,
                clientSubtitles = clientSubtitles,
                losslessAudio = losslessAudio,
                serverAudioFallback = losslessAudio && audioOverride == null,
            )
        }

        /** With this device's current overrides. */
        fun current(selection: TrackSelection?, playback: PlaybackSettings?) = resolve(
            selection, playback, TrackOverrides.audioLanguage, TrackOverrides.subtitleLanguage, TrackOverrides.subtitleMode,
            TrackOverrides.losslessAudio,
        )
    }
}

/** An audio track as the server's selector sees it. */
fun Format.audioCandidate() = ClientTrackSelector.Audio(language, label.orEmpty(), when (sampleMimeType) {
    "audio/eac3", "audio/eac3-joc" -> "eac3"
    "audio/ac3" -> "ac3"
    "audio/mp4a-latm" -> "aac"
    "audio/mpeg" -> "mp3"
    "audio/true-hd" -> "truehd"
    "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.uhd;profile=p2" -> "dts"
    else -> sampleMimeType?.substringAfter('/') ?: ""
})

/** A subtitle track as the server's selector sees it (ffprobe codec names). */
fun Format.subtitleCandidate() = ClientTrackSelector.Subtitle(language, label.orEmpty(), when (subtitleMime) {
    "application/pgs" -> "hdmv_pgs_subtitle"
    "application/vobsub" -> "dvd_subtitle"
    "application/x-subrip" -> "subrip"
    "text/x-ssa" -> "ass"
    "text/vtt" -> "webvtt"
    "application/dvbsubs" -> "dvb_subtitle"
    else -> subtitleMime?.substringAfter('/') ?: ""
}, forced = selectionFlags and C.SELECTION_FLAG_FORCED != 0)
