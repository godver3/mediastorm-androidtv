package com.strmr.tv.player

/**
 * Regional dubs of a language (Québec French, Latin American Spanish, ...), as the server's
 * selector (`backend/services/trackselect/region.go`) sees them: a region-tagged preference
 * ("fr-CA", "es-419") picks that dub first and falls back to any track of the language.
 * Releases rarely tag these in the container language (both French dubs are usually
 * "fre"), so a track is recognised by a region-tagged language or by its labels ("VFQ",
 * "French (Canadien)", "FR-CA 5.1", "Latino").
 */
object RegionalAudio {
    data class Variant(val language: String, val regions: Set<String>, val words: Set<String>, val name: String)

    private val variants = listOf(
        Variant("fra", setOf("ca", "qc"), setOf("vfq", "vq", "quebec", "quebecois", "quebecoise", "canada", "canadian", "canadien", "canadienne"), "French (Canada)"),
        Variant("fra", setOf("fr", "be", "ch"), setOf("vff", "vfi", "france", "parisian"), "French (France)"),
        Variant("por", setOf("br"), setOf("brazil", "brazilian", "brasil", "brasileiro", "ptbr"), "Portuguese (Brazil)"),
        Variant("por", setOf("pt"), setOf("portugal", "ptpt", "europeu"), "Portuguese (Portugal)"),
        Variant("spa", setOf("419", "mx", "latam", "la", "ar", "co", "cl", "us"),
                setOf("latino", "latin", "latam", "latinoamerica", "latinoamericano", "mexico", "mexican", "neutro"), "Spanish (Latin America)"),
        Variant("spa", setOf("es"), setOf("castilian", "castellano", "spain", "espana", "iberian"), "Spanish (Spain)"),
    )

    /** The audio settings' regional choices: setting code and name. */
    val options = listOf(
        "fr-CA" to "French (Canada)", "fr-FR" to "French (France)",
        "es-419" to "Spanish (Latin America)", "es-ES" to "Spanish (Spain)",
        "pt-BR" to "Portuguese (Brazil)", "pt-PT" to "Portuguese (Portugal)",
    )

    private val folds = mapOf('é' to 'e', 'è' to 'e', 'ê' to 'e', 'ç' to 'c', 'ñ' to 'n', 'ã' to 'a', 'á' to 'a', 'í' to 'i', 'ó' to 'o', 'ú' to 'u')

    private fun split(tag: String?): Pair<String, String> {
        val t = tag?.trim()?.lowercase().orEmpty()
        val i = t.indexOfFirst { it == '-' || it == '_' }
        return if (i > 0) t.substring(0, i) to t.substring(i + 1) else t to ""
    }

    /** The variant a preference such as "fr-CA" asks for; null for a plain language. */
    fun variant(preference: String?): Variant? {
        val (language, region) = split(preference)
        if (region.isEmpty()) return null
        val code = ClientTrackSelector.normalize(language) ?: return null
        return variants.firstOrNull { it.language == code && region in it.regions }
    }

    /** Whether two preferences ask for the same regional dub (or both for none). */
    fun sameVariant(a: String?, b: String?) = variant(a) == variant(b)

    /** "French (Canada)" for fr-CA; null for a plain language. */
    fun displayName(preference: String?): String? = variant(preference)?.name

    fun matches(variant: Variant, language: String?, title: String?): Boolean {
        val (_, region) = split(language)
        if (region.isNotEmpty() && region in variant.regions) return true
        val label = "${language.orEmpty()} ${title.orEmpty()}".lowercase().map { folds[it] ?: it }.joinToString("")
        val words = label.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        return words.withIndex().any { (i, word) ->
            // A region right after its language ("FR-CA", "French CA", a "CA" title on a "fre" track).
            word in variant.words || (i > 0 && word in variant.regions && ClientTrackSelector.normalize(words[i - 1]) == variant.language)
        }
    }
}
