package com.strmr.tv.player

import java.util.Locale

/**
 * The languages the online subtitle search offers, as the Expo app's
 * `PLAYER_LANGUAGE_OPTIONS`: the search takes the 2-letter `code`, profile settings use the
 * 3-letter `settingCode`.
 */
object SubtitleLanguages {
    data class Option(val code: String, val settingCode: String, val name: String)

    val options = listOf(
        Option("en", "eng", "English"), Option("fr", "fra", "French"), Option("es", "spa", "Spanish"),
        Option("de", "deu", "German"), Option("it", "ita", "Italian"), Option("pt", "por", "Portuguese"),
        Option("nl", "nld", "Dutch"), Option("pl", "pol", "Polish"), Option("ru", "rus", "Russian"),
        Option("ja", "jpn", "Japanese"), Option("ko", "kor", "Korean"), Option("zh", "zho", "Chinese"),
        Option("ar", "ara", "Arabic"), Option("he", "heb", "Hebrew"), Option("sv", "swe", "Swedish"),
        Option("no", "nor", "Norwegian"), Option("da", "dan", "Danish"), Option("fi", "fin", "Finnish"),
        Option("hr", "hrv", "Croatian"), Option("sr", "srp", "Serbian"), Option("bs", "bos", "Bosnian"),
    )

    private val aliases: Map<String, String> = buildMap {
        options.forEach { put(it.code, it.settingCode); put(it.settingCode, it.settingCode); put(it.name.lowercase(), it.settingCode) }
        put("fre", "fra"); put("ger", "deu"); put("dut", "nld"); put("chi", "zho")
        put("nob", "nor"); put("nno", "nor")
    }

    /** A language as its 3-letter setting code, or null when it isn't recognisable. */
    fun canonical(value: String?): String? {
        val v = value?.trim()?.lowercase()?.ifEmpty { null } ?: return null
        aliases[v]?.let { return it }
        aliases[v.split('-', '_').first()]?.let { return it }
        v.split(Regex("[^a-z]+")).forEach { token -> aliases[token]?.let { return it } }
        return v.takeIf { it.length == 3 }
    }

    /** The search's languages, limited to the profile's allowed track languages when it sets any. */
    fun allowed(allowedTrackLanguages: List<String>): List<Option> {
        if (allowedTrackLanguages.isEmpty()) return options
        val allowed = allowedTrackLanguages.mapNotNull(::canonical).toSet()
        return options.filter { it.settingCode in allowed }
    }

    /** The search's 2-letter code for a profile language ("eng" → "en"); unknown codes pass through. */
    fun searchCode(language: String): String =
        canonical(language)?.let { c -> options.firstOrNull { it.settingCode == c }?.code } ?: language

    fun isEnglish(language: String?): Boolean = language?.lowercase() in setOf("en", "eng", "english")

    /** "French" for fr / fra / fre, "French (Canada)" for fr-CA. */
    fun displayName(language: String): String {
        RegionalAudio.displayName(language)?.let { return it }
        val c = canonical(language)
        options.firstOrNull { it.settingCode == c }?.let { return it.name }
        return Locale.forLanguageTag(language).displayLanguage.ifEmpty { language }
    }
}
