package com.strmr.tv.player

import java.util.Locale

/**
 * The server's track selector (`backend/services/trackselect`) on the client, for when this
 * device overrides the profile's languages or subtitle mode: the same language matching
 * (codes, names, words of a track's label), audio codec order (E-AC-3, then AAC / AC-3 / MP3,
 * then DTS and the like, TrueHD last; commentary only when nothing else matches) and
 * subtitle priorities (forced tracks only for forced-only; for on, non-forced SDH, then
 * full / plain or dubtitles depending on the audio language, then non-signs, then signs,
 * then a forced track). Indices are positions in the given lists; -1 is none.
 */
object ClientTrackSelector {
    data class Audio(val language: String?, val title: String, val codec: String)
    data class Subtitle(val language: String?, val title: String, val codec: String, val forced: Boolean)

    private val bibliographic = mapOf(
        "chi" to "zho", "dut" to "nld", "cze" to "ces", "gre" to "ell", "rum" to "ron", "fre" to "fra", "ger" to "deu",
        "per" to "fas", "slo" to "slk", "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "geo" to "kat",
        "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa", "tib" to "bod", "wel" to "cym",
    )
    private val iso3: Set<String> by lazy {
        Locale.getISOLanguages().mapNotNull { runCatching { Locale(it).isO3Language }.getOrNull()?.ifEmpty { null } }.toSet()
    }
    private val names: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { code ->
            val locale = Locale(code)
            val three = runCatching { locale.isO3Language }.getOrNull()?.ifEmpty { null } ?: return@mapNotNull null
            locale.getDisplayLanguage(Locale.ENGLISH).lowercase() to three
        }.toMap()
    }

    /** A language code, tag or English name as its ISO 639-2/T code; null when it isn't one. */
    fun normalize(value: String?): String? {
        val v = value?.trim()?.lowercase()?.ifEmpty { null } ?: return null
        val base = v.split('-', '_').first()
        bibliographic[base]?.let { return it }
        if (base.length == 2) return runCatching { Locale(base).isO3Language }.getOrNull()?.ifEmpty { null }
        if (base.length == 3 && base in iso3) return base
        return names[v]
    }

    /** The server's `MatchesLanguage`: the track's language, or a word of its language or label. */
    fun matchesLanguage(language: String?, title: String?, preferred: String): Boolean {
        val pref = preferred.trim().lowercase().ifEmpty { return false }
        val lang = language.orEmpty().trim().lowercase()
        val label = title.orEmpty().trim().lowercase()
        val code = normalize(pref)
        if (code != null) {
            if (normalize(lang) == code) return true
            return "$lang $label".split(Regex("[^a-z]+")).any { it.isNotEmpty() && normalize(it) == code }
        }
        if (lang == pref || label == pref) return true
        if (lang.isNotEmpty() && (pref in lang || lang in pref)) return true
        return label.isNotEmpty() && (pref in label || label in pref)
    }

    /** Commentary or audio description ("French (France) AD"): not the main mix. */
    fun isCommentary(title: String) = title.lowercase().let { t ->
        listOf("commentary", "isolated score", "music only", "score only", "audio description", "audiodescription",
               "audio-description", "descriptive audio", "described video").any { it in t } ||
            t.split(Regex("[^a-z]+")).any { it == "ad" }
    }
    private fun isTrueHd(codec: String) = codec.lowercase() in setOf("truehd", "mlp")
    private val compatible = setOf("aac", "ac3", "eac3", "mp3")

    /** A region-tagged preference ("fr-CA") first tries that regional dub ([RegionalAudio]). */
    fun findAudio(tracks: List<Audio>, preferred: String?): Int {
        if (preferred.isNullOrBlank() || tracks.isEmpty()) return -1
        val passes: List<(Audio) -> Boolean> = listOf(
            { it.codec.equals("eac3", true) }, { it.codec.lowercase() in compatible }, { !isTrueHd(it.codec) }, { isTrueHd(it.codec) },
        )
        RegionalAudio.variant(preferred)?.let { variant ->
            for (pass in passes) {
                tracks.indexOfFirst {
                    matchesLanguage(it.language, it.title, preferred) && !isCommentary(it.title) &&
                        RegionalAudio.matches(variant, it.language, it.title) && pass(it)
                }.takeIf { it >= 0 }?.let { return it }
            }
        }
        for (commentaryAllowed in listOf(false, true)) {
            for (pass in passes) {
                // The last commentary pass is any matching track.
                tracks.indexOfFirst {
                    matchesLanguage(it.language, it.title, preferred) && (commentaryAllowed || !isCommentary(it.title)) && pass(it)
                }.takeIf { it >= 0 }?.let { return it }
            }
        }
        return -1
    }

    /** The preferred language when it is allowed (and present), else the first allowed language present. */
    fun findAllowedAudio(tracks: List<Audio>, allowed: List<String>, preferred: String?): Int {
        val allow = allowed.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        if (allow.isEmpty()) return findAudio(tracks, preferred)
        val pref = preferred?.trim()?.lowercase().orEmpty()
        if (pref.isNotEmpty() && allow.any { matchesLanguage(pref, "", it) }) {
            findAudio(tracks, pref).takeIf { it >= 0 }?.let { return it }
        }
        for (language in allow) findAudio(tracks, language).takeIf { it >= 0 }?.let { return it }
        return -1
    }

    private fun isUnsupported(codec: String) = codec.lowercase().let { "vobsub" in it || "dvd_subtitle" in it || it == "dvdsub" }
    private fun isSdh(t: String) = t.lowercase().let { "sdh" in it || "deaf" in it || "hard of hearing" in it }
    private fun isSigns(t: String) = t.lowercase().let { "sign" in it || "song" in it }
    private fun isDubtitle(t: String) = "dubtitle" in t.lowercase()
    private fun isFullOrPlain(t: String) = "full sub" in t.lowercase() || t.isBlank()
    private fun forced(s: Subtitle) = s.forced || "forced" in s.title.lowercase()

    fun findSubtitle(tracks: List<Subtitle>, preferred: String?, mode: String?, audioLanguage: String?): Int {
        if (tracks.isEmpty() || mode == null || mode == "off") return -1
        val selectable = tracks.withIndex().filter { !isUnsupported(it.value.codec) }
        if (selectable.isEmpty()) return -1
        val pref = preferred?.trim()?.lowercase().orEmpty()
        fun matches(s: Subtitle) = matchesLanguage(s.language, s.title, pref)
        if (mode == "forced-only") return selectable.firstOrNull { forced(it.value) && matches(it.value) }?.index ?: -1
        if (pref.isEmpty()) return -1
        val nonForced = selectable.filter { !forced(it.value) && matches(it.value) }
        if (nonForced.isNotEmpty()) {
            val sameLanguage = matchesLanguage(audioLanguage.orEmpty().trim().lowercase(), "", pref)
            val order: List<(Subtitle) -> Boolean> = listOf<(Subtitle) -> Boolean>({ isSdh(it.title) && !isSigns(it.title) }) +
                (if (sameLanguage) listOf({ s: Subtitle -> isDubtitle(s.title) }, { s: Subtitle -> isFullOrPlain(s.title) })
                else listOf({ s: Subtitle -> isFullOrPlain(s.title) }, { s: Subtitle -> isDubtitle(s.title) })) +
                listOf({ s: Subtitle -> !isSigns(s.title) })
            for (rule in order) nonForced.firstOrNull { rule(it.value) }?.let { return it.index }
            return nonForced.first().index
        }
        return selectable.firstOrNull { forced(it.value) && matches(it.value) }?.index ?: -1
    }
}
