package com.strmr.tv.player

import com.strmr.tv.api.ProbedAudioTrack
import com.strmr.tv.api.ProbedSubtitleTrack
import com.strmr.tv.api.SelectedTrack
import com.strmr.tv.api.TrackSelection

/**
 * The tracks picked by hand for one manual-selection result (the Expo app's
 * `ManualTrackOverrides`): container (ffprobe) stream numbers, null where the user kept the
 * default, [OFF] for subtitles turned off.
 */
data class ManualTrackChoice(val audio: Int? = null, val subtitle: Int? = null) {
    val isEmpty: Boolean get() = audio == null && subtitle == null

    companion object {
        const val OFF = -1
    }
}

/**
 * The pure parts of the Expo manual-selection track panel (`manual-selection.tsx`): which
 * probed tracks it lists (the profile's allowed languages, as `filterPlayerTracksByAllowedLanguages`
 * with unknown languages kept), which it preselects (`findSelectedAudioTrack` /
 * `findSelectedSubtitleTrack`) and how a choice reaches the player (container stream numbers,
 * as `playback.ts` passes `trackOverrides`).
 */
object ManualTracks {
    private val compatible = setOf("aac", "ac3", "eac3", "mp3")
    private val trueHd = setOf("truehd", "mlp")

    /** The panel's language test: an exact or partial match on the track's language or title. */
    fun matchesLanguage(language: String?, title: String?, preferred: String?): Boolean {
        val pref = preferred?.trim()?.lowercase().orEmpty().ifEmpty { return false }
        // A regional preference ("fr-CA") matches its language; [defaultAudio] prefers the dub.
        if (RegionalAudio.variant(pref) != null) return ClientTrackSelector.matchesLanguage(language, title, pref)
        val lang = language.orEmpty().lowercase()
        val t = title.orEmpty().lowercase()
        if (lang == pref || t == pref) return true
        if (lang.isNotEmpty() && (pref in lang || lang in pref)) return true
        return t.isNotEmpty() && (pref in t || t in pref)
    }

    private fun isCommentary(title: String?) = ClientTrackSelector.isCommentary(title.orEmpty())

    private fun codec(t: ProbedAudioTrack) = t.codec.lowercase()

    /** The first compatible non-commentary track, else non-TrueHD non-commentary, else the first. */
    private fun firstCompatible(tracks: List<ProbedAudioTrack>): Int? =
        tracks.firstOrNull { codec(it) in compatible && !isCommentary(it.title) }?.index
            ?: tracks.firstOrNull { codec(it) !in trueHd && !isCommentary(it.title) }?.index
            ?: tracks.firstOrNull()?.index

    /** The audio track preselected for [preferred]; null when there are no tracks. */
    fun defaultAudio(tracks: List<ProbedAudioTrack>, preferred: String?): Int? {
        if (tracks.isEmpty()) return null
        if (preferred.isNullOrBlank()) return firstCompatible(tracks)
        val passes: List<(ProbedAudioTrack) -> Boolean> = listOf(
            { codec(it) in compatible && !isCommentary(it.title) },
            { codec(it) !in trueHd && !isCommentary(it.title) },
            { !isCommentary(it.title) },
            { codec(it) in compatible },
            { codec(it) !in trueHd },
            { true },
        )
        RegionalAudio.variant(preferred)?.let { variant ->
            val regional = tracks.filter { RegionalAudio.matches(variant, it.language, it.title) && !isCommentary(it.title) }
            for (pass in passes.take(3)) {
                regional.firstOrNull { matchesLanguage(it.language, it.title, preferred) && pass(it) }?.let { return it.index }
            }
        }
        for (pass in passes) {
            tracks.firstOrNull { matchesLanguage(it.language, it.title, preferred) && pass(it) }?.let { return it.index }
        }
        return firstCompatible(tracks)
    }

    private fun isSdh(t: ProbedSubtitleTrack) = t.title.orEmpty().lowercase().let {
        "sdh" in it || "deaf" in it || "hard of hearing" in it
    }

    /** The subtitle track preselected for the language and mode (off when unset); null is Off. */
    fun defaultSubtitle(tracks: List<ProbedSubtitleTrack>, preferred: String?, mode: String?): Int? {
        val m = mode ?: "off"
        if (m == "off" || tracks.isEmpty() || preferred.isNullOrBlank()) return null
        fun matches(t: ProbedSubtitleTrack) = matchesLanguage(t.language, t.title, preferred)
        if (m == "forced-only") return tracks.firstOrNull { it.forced && matches(it) }?.index
        return tracks.firstOrNull { !it.forced && isSdh(it) && matches(it) }?.index
            ?: tracks.firstOrNull { !it.forced && !isSdh(it) && matches(it) }?.index
            ?: tracks.firstOrNull { it.forced && matches(it) }?.index
    }

    /**
     * The tracks whose language the profile allows (all of them when it allows every language,
     * or when none of the tracks is in an allowed language); tracks of unknown language stay.
     */
    fun <T> allowed(tracks: List<T>, allowedLanguages: List<String>, language: (T) -> String?): List<T> {
        if (allowedLanguages.isEmpty()) return tracks
        val allow = allowedLanguages.mapNotNull { SubtitleLanguages.canonical(it) }.toSet()
        fun ok(t: T, unknown: Boolean) = SubtitleLanguages.canonical(language(t))?.let { it in allow } ?: unknown
        if (tracks.none { ok(it, false) }) return tracks
        return tracks.filter { ok(it, true) }
    }

    /** The container's undetermined language tags carry no language for matching. */
    private fun languageOf(raw: String) = raw.trim().lowercase().takeIf { it.isNotEmpty() && it != "und" }

    /**
     * The server's track choice with the hand-picked tracks in place: the player maps a
     * [SelectedTrack] by container number, then by position, then by language
     * ([TrackPreferences]). Off turns subtitles off. A DVD (VobSub) subtitle is ignored, as on
     * the Expo native players, leaving the server's subtitle choice.
     */
    fun applied(
        base: TrackSelection?,
        choice: ManualTrackChoice,
        audio: List<ProbedAudioTrack>,
        subtitles: List<ProbedSubtitleTrack>,
    ): TrackSelection {
        var out = base ?: TrackSelection()
        choice.audio?.let { index ->
            audio.indexOfFirst { it.index == index }.takeIf { it >= 0 }?.let { pos ->
                val t = audio[pos]
                val language = languageOf(t.language)
                val same = audio.filter { languageOf(it.language) == language }
                out = out.copy(audio = SelectedTrack(
                    streamIndex = t.index, typeIndex = pos, language = language, languageCode = language,
                    languageIndex = same.indexOf(t).coerceAtLeast(0), languageCount = same.size,
                    codec = t.codec, title = t.title,
                ))
            }
        }
        when (val index = choice.subtitle) {
            null -> {}
            ManualTrackChoice.OFF -> out = out.copy(subtitle = null, textSubtitle = null, subtitleMode = "off")
            else -> subtitles.indexOfFirst { it.index == index }.takeIf { it >= 0 && !isDvd(subtitles[it].codec) }?.let { pos ->
                val t = subtitles[pos]
                val language = languageOf(t.language)
                val same = subtitles.filter { languageOf(it.language) == language }
                val pick = SelectedTrack(
                    streamIndex = t.index, typeIndex = pos, language = language, languageCode = language,
                    languageIndex = same.indexOf(t).coerceAtLeast(0), languageCount = same.size,
                    codec = t.codec, title = t.title, forced = t.forced, bitmap = t.isBitmap,
                )
                out = out.copy(subtitle = pick, textSubtitle = pick.takeIf { !t.isBitmap }, subtitleMode = "on")
            }
        }
        return out
    }

    private fun isDvd(codec: String) = codec.lowercase().let { "dvd_subtitle" in it || it == "dvdsub" || "vobsub" in it }

    /** "English · Commentary · EAC3 5.1(side)" style line for an audio track. */
    fun audioLabel(t: ProbedAudioTrack): String =
        listOfNotNull(languageName(t.language), t.title?.ifBlank { null }).joinToString(" · ")

    fun audioDetail(t: ProbedAudioTrack): String =
        listOfNotNull(t.codec.ifBlank { null }?.uppercase(), t.profile?.ifBlank { null }).joinToString(" · ")

    fun subtitleLabel(t: ProbedSubtitleTrack): String =
        listOfNotNull(languageName(t.language), t.title?.ifBlank { null }).joinToString(" · ")

    fun subtitleDetail(t: ProbedSubtitleTrack): String = buildList {
        if (t.forced) add("Forced")
        add(if (t.isBitmap) t.bitmapType?.ifBlank { null } ?: "PGS" else codecName(t.codec))
    }.filter { it.isNotBlank() }.joinToString(" · ")

    private fun codecName(codec: String) = when (codec.lowercase()) {
        "subrip", "srt" -> "SRT"
        "ass", "ssa" -> "ASS"
        "webvtt" -> "WebVTT"
        "mov_text" -> "TX3G"
        "hdmv_pgs_subtitle" -> "PGS"
        "dvd_subtitle" -> "VobSub"
        else -> codec.uppercase()
    }

    fun languageName(raw: String): String = languageOf(raw)?.let { SubtitleLanguages.displayName(it) } ?: "Unknown"
}
