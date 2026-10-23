package com.strmr.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.strmr.tv.api.SelectedTrack
import com.strmr.tv.api.TrackSelection
import java.util.Locale

/** One playable track of a kind, in container order, as the menus and the mapping see it. */
data class PlayerTrack(val group: Tracks.Group, val index: Int, val typeIndex: Int) {
    val format: Format get() = group.getTrackFormat(index)
    val selected: Boolean get() = group.isTrackSelected(index)
    val supported: Boolean get() = group.isTrackSupported(index)
}

/** The tracks of `type` (C.TRACK_TYPE_AUDIO / TEXT), in container order. */
fun Tracks.ofType(type: Int): List<PlayerTrack> {
    val out = mutableListOf<PlayerTrack>()
    for (group in groups) {
        if (group.type != type) continue
        for (i in 0 until group.length) out += PlayerTrack(group, i, out.size)
    }
    return out
}

/**
 * The profile's audio / subtitle choice for one stream, as resolved by the backend (prequeue
 * status or `/playback/resolve`), applied to ExoPlayer's tracks once they are known: by
 * container number, else by `typeIndex`, else by language + `languageIndex`. No subtitle pick
 * (or mode `off`) leaves subtitles off, as on tvOS. Where this device overrides a choice
 * ([EffectiveTracks]), the track is picked here by the server's rules instead.
 */
class TrackPreferences(private val selection: TrackSelection?, private val effective: EffectiveTracks? = null) {
    private val subtitlesOff = selection?.subtitleMode == "off"
    private val subtitle: SelectedTrack? = if (subtitlesOff) null else selection?.subtitle
    /** The subtitle track [apply] turned on, if any. */
    var subtitlePick: PlayerTrack? = null
        private set

    fun apply(tracks: Tracks, params: TrackSelectionParameters): TrackSelectionParameters {
        val builder = params.buildUpon()
        val audio = tracks.ofType(C.TRACK_TYPE_AUDIO).filter { it.supported }
        val audioPick = if (effective?.clientAudio == true) {
            audio.getOrNull(ClientTrackSelector.findAllowedAudio(audio.map { it.format.audioCandidate() },
                                                                 effective.allowedLanguages, effective.audioLanguage))
        } else {
            selection?.audio?.let { match(it, audio) }
        }
        audioPick?.let { builder.setOverrideForType(TrackSelectionOverride(it.group.mediaTrackGroup, it.index)) }
        val text = tracks.ofType(C.TRACK_TYPE_TEXT).filter { it.supported && !isClosedCaption(it.format) }
        val pick = if (effective?.clientSubtitles == true) {
            val audioLanguage = audioPick?.format?.language ?: selection?.audio?.language ?: effective.audioLanguage
            text.getOrNull(ClientTrackSelector.findSubtitle(text.map { it.format.subtitleCandidate() },
                                                            effective.subtitleLanguage, effective.subtitleMode, audioLanguage))
        } else {
            subtitle?.let { match(it, text) }
        }
        subtitlePick = pick
        if (pick != null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(pick.group.mediaTrackGroup, pick.index))
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        }
        return builder.build()
    }

    private fun match(pick: SelectedTrack, tracks: List<PlayerTrack>): PlayerTrack? {
        val language = pick.languageCode ?: pick.language
        fun ok(t: PlayerTrack) = language == null || sameLanguage(t.format.language, language)
        // Matroska and MP4 number tracks from 1; ffprobe's stream index counts from 0.
        tracks.firstOrNull { it.format.id?.toIntOrNull() == pick.streamIndex + 1 }?.takeIf(::ok)?.let { return it }
        tracks.getOrNull(pick.typeIndex)?.takeIf(::ok)?.let { return it }
        val same = tracks.filter { sameLanguage(it.format.language, language) }
        return same.getOrNull(pick.languageIndex.coerceAtMost(same.size - 1))
    }

    companion object {
        fun isClosedCaption(format: Format) =
            format.subtitleMime in setOf("application/cea-608", "application/cea-708")

        /** Compares ISO 639-1 / 639-2 (B or T) tags through their 3-letter form. */
        fun sameLanguage(a: String?, b: String?): Boolean {
            if (a == null || b == null) return a == null && b == null
            return normalize(a) == normalize(b)
        }

        private fun normalize(tag: String): String {
            val lower = tag.lowercase().substringBefore('-')
            val bibliographic = mapOf(
                "ger" to "deu", "fre" to "fra", "dut" to "nld", "chi" to "zho", "cze" to "ces", "gre" to "ell",
                "per" to "fas", "rum" to "ron", "slo" to "slk", "alb" to "sqi", "arm" to "hye", "baq" to "eus",
                "bur" to "mya", "geo" to "kat", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa",
                "tib" to "bod", "wel" to "cym",
            )
            val code = bibliographic[lower] ?: lower
            return runCatching { Locale(code).isO3Language }.getOrNull()?.ifEmpty { null } ?: code
        }
    }
}

/**
 * A subtitle's own format: Media3 parses subtitles while extracting, so `sampleMimeType` is
 * its cue format and the original one is in `codecs`.
 */
val Format.subtitleMime: String?
    get() = if (sampleMimeType == "application/x-media3-cues") codecs else sampleMimeType

/** Menu title: the language name, else the track's label. */
fun Format.menuTitle(fallback: String): String {
    val lang = language?.takeIf { it != C.LANGUAGE_UNDETERMINED && it.isNotBlank() }
    val name = lang?.let { Locale.forLanguageTag(it).displayLanguage.ifEmpty { null } }
    return name ?: label ?: fallback
}

/** Audio menu detail: format, channels and the track's own label ("E-AC-3 5.1 · Commentary"). */
fun Format.audioDetail(): String {
    val codec = when (sampleMimeType) {
        "audio/eac3", "audio/eac3-joc" -> "E-AC-3"
        "audio/ac3" -> "AC-3"
        "audio/true-hd" -> "TrueHD"
        "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.uhd;profile=p2" -> "DTS"
        "audio/mp4a-latm" -> "AAC"
        "audio/flac" -> "FLAC"
        "audio/opus" -> "Opus"
        else -> sampleMimeType?.substringAfter('/')?.uppercase() ?: ""
    }
    val channels = when (channelCount) { 8 -> " 7.1"; 6 -> " 5.1"; 2 -> " Stereo"; 1 -> " Mono"; else -> "" }
    val parts = mutableListOf(codec + channels)
    if (sampleMimeType == "audio/eac3-joc") parts += "Atmos"
    label?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}

/** Subtitle menu detail: flags, the track's label and the format ("Forced · PGS"). */
fun Format.subtitleDetail(): String {
    val parts = mutableListOf<String>()
    if (selectionFlags and C.SELECTION_FLAG_FORCED != 0) parts += "Forced"
    if (roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) parts += "SDH"
    label?.takeIf { it.isNotBlank() }?.let { parts += it }
    parts += when (val mime = subtitleMime) {
        "application/x-subrip" -> "SRT"
        "text/x-ssa" -> "ASS"
        "text/vtt" -> "WebVTT"
        "application/pgs" -> "PGS"
        "application/vobsub" -> "VobSub"
        "application/dvbsubs" -> "DVB"
        else -> (mime ?: "").substringAfter('/').substringAfter("x-").uppercase()
    }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}
