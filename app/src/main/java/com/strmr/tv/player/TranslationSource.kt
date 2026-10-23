package com.strmr.tv.player

import com.strmr.tv.api.SubtitleStreamInfo

/** Where a Translate… source track's language and extraction number come from. */
object TranslationSource {
    /** The Expo player's `langMap`: codes and names (English and native) to the translator's 2-letter codes. */
    private val langMap: Map<String, String> = buildMap {
        fun add(code: String, vararg aliases: String) { put(code, code); aliases.forEach { put(it, code) } }
        add("en", "eng", "english")
        add("fr", "fre", "fra", "french", "français")
        add("de", "ger", "deu", "german", "deutsch")
        add("es", "spa", "spanish", "español")
        add("it", "ita", "italian", "italiano")
        add("pt", "por", "portuguese", "português")
        add("nl", "dut", "nld", "dutch", "nederlands")
        add("pl", "pol", "polish", "polski")
        add("ru", "rus", "russian", "русский")
        add("ja", "jpn", "japanese", "日本語")
        add("ko", "kor", "korean")
        add("zh", "chi", "zho", "chinese")
        add("ar", "ara", "arabic")
        add("he", "heb", "hebrew")
        add("sv", "swe", "swedish")
        add("no", "nor", "nob", "nno", "norwegian")
        add("da", "dan", "danish")
        add("fi", "fin", "finnish")
        add("hr", "hrv", "croatian")
        add("sr", "srp", "serbian")
        add("bs", "bos", "bosnian")
    }
    private val separators = Regex("[\\s·,]+")

    /**
     * The source language for `/api/subtitles/translate`, as the Expo player finds it: the
     * track's metadata language (mapped, else passed as it is), else a language named in its
     * label (a non-English word first, then any 3-letter code), else English.
     */
    fun language(metadataLanguage: String?, label: String?): String {
        metadataLanguage?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" }?.let { return langMap[it] ?: it }
        val words = label.orEmpty().lowercase().split(separators).map { it.trim() }.filter { it.isNotEmpty() }
        words.firstNotNullOfOrNull { w -> langMap[w]?.takeIf { it != "en" } }?.let { return it }
        words.firstNotNullOfOrNull { w -> if (w.length == 3) langMap[w] else null }?.let { return it }
        return "en"
    }

    /**
     * A player track's number for `/api/video/subtitles/start` (its position among the file's
     * subtitle streams): the server stream with the same container number (Matroska and MP4
     * number tracks from 1, ffprobe from 0; the probe's `index` and `absoluteIndex` both carry
     * it), else the track's position in the player's own list.
     */
    fun extractIndex(containerId: Int?, streams: List<SubtitleStreamInfo>, playerPosition: Int): Int {
        if (containerId != null && streams.isNotEmpty()) {
            val number = containerId - 1
            streams.indexOfFirst { it.absoluteIndex == number || it.index == number }.takeIf { it >= 0 }?.let { return it }
        }
        return playerPosition
    }

    /** The server stream for a player track, when the server sent its streams. */
    fun stream(containerId: Int?, streams: List<SubtitleStreamInfo>): SubtitleStreamInfo? =
        containerId?.let { id -> streams.firstOrNull { it.absoluteIndex == id - 1 || it.index == id - 1 } }
}
