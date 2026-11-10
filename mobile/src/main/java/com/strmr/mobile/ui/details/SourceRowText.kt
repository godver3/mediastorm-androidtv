package com.strmr.mobile.ui.details

/**
 * What a source row says about a result, from its scraper attributes (as the TV source list):
 * the release name, the quality badges and the details line. Pure, so it is unit tested.
 */
object SourceRowText {
    /** The release name: the first line of the scraper's raw title when present. */
    fun release(title: String, attributes: Map<String, String>): String =
        (attributes["raw_title"] ?: title).lineSequence().firstOrNull()?.ifEmpty { null } ?: title

    /** "4K", "DV", "HDR10+" / "HDR", the quality and the codec, in that order. */
    fun badges(attributes: Map<String, String>): List<String> = buildList {
        attributes["resolution"]?.takeIf { it.isNotEmpty() }?.let { add(if (it == "2160p") "4K" else it) }
        if (attributes["hasDV"] == "true") add("DV")
        attributes["hdr"]?.let { if (it.contains("HDR10+")) add("HDR10+") else if (it.contains("HDR")) add("HDR") }
        attributes["quality"]?.takeIf { it.isNotEmpty() }?.let { add(it) }
        attributes["codec"]?.takeIf { it.isNotEmpty() }?.let { add(it.uppercase()) }
    }

    /**
     * The size, where it comes from (Usenet, else the tracker or indexer), seeders, audio,
     * channels and languages, and "Tracks chosen" once tracks were picked for it.
     */
    fun details(
        attributes: Map<String, String>,
        size: String?,
        serviceType: String,
        indexer: String,
        tracksChosen: Boolean,
    ): List<String> {
        val usenet = serviceType == "usenet"
        return buildList {
            size?.let { add(it) }
            add(if (usenet) "Usenet" else attributes["tracker"] ?: indexer)
            attributes["seeders"]?.takeIf { it.isNotEmpty() && !usenet }?.let { add("$it seeders") }
            attributes["audio"]?.takeIf { it.isNotEmpty() }?.let { add(it) }
            attributes["channels"]?.takeIf { it.isNotEmpty() }?.let { add(it) }
            attributes["languages"]?.takeIf { it.isNotEmpty() }?.let { add(it.replace(",", " ").replace("⚙", "").trim()) }
            if (tracksChosen) add("Tracks chosen")
        }.filter { it.isNotBlank() }
    }

    /** Debrid cache / usenet health as one of three looks. */
    enum class Health { PENDING, GOOD, BAD, NONE }

    fun health(state: String?): Health = when (state) {
        "pending" -> Health.PENDING
        "healthy", "cached" -> Health.GOOD
        "unhealthy", "not_cached" -> Health.BAD
        else -> Health.NONE
    }
}
