package com.strmr.tv.player

import com.strmr.tv.api.SubtitleSearchResult

/**
 * Picking an online subtitle for the stream, ported from the Expo app's
 * `utils/subtitle-helpers.ts` (same tokens, weights and tie-breaks).
 */
object SubtitleMatching {
    private val extension = Regex("\\.(mkv|mp4|avi|srt|sub)$", RegexOption.IGNORE_CASE)
    private val delimiters = Regex("[._-]")
    private val whitespace = Regex("\\s+")

    private val importantPatterns = listOf(
        Regex("^\\d{3,4}p$"), // resolution: 720p, 1080p, 2160p
        Regex("^(bluray|bdrip|brrip|webrip|web-dl|webdl|hdtv|hdrip|dvdrip)$", RegexOption.IGNORE_CASE), // source
        Regex("^(x264|x265|h264|h265|hevc|avc|xvid)$", RegexOption.IGNORE_CASE), // codec
        Regex("^(dts|ac3|aac|truehd|atmos|dd5|ddp5|eac3)$", RegexOption.IGNORE_CASE), // audio
        Regex("^(hdr|hdr10|dv|dolby|vision)$", RegexOption.IGNORE_CASE), // HDR
    )

    private fun tokens(s: String): Set<String> =
        s.lowercase().replace(extension, "").replace(delimiters, " ").split(whitespace).filter { it.length > 1 }.toSet()

    /**
     * How close a subtitle's release name is to the stream's: the percentage of the stream's
     * tokens it shares, plus 2 for each shared resolution / source / codec / audio / HDR token.
     */
    fun releaseSimilarity(mediaRelease: String?, subtitleRelease: String?): Double {
        if (mediaRelease.isNullOrEmpty() || subtitleRelease.isNullOrEmpty()) return 0.0
        val media = tokens(mediaRelease)
        val sub = tokens(subtitleRelease)
        if (media.isEmpty() || sub.isEmpty()) return 0.0
        var matches = 0
        var bonus = 0
        for (token in media) {
            if (token !in sub) continue
            matches++
            if (importantPatterns.any { it.matches(token) }) bonus += 2
        }
        return matches.toDouble() / media.size * 100 + bonus
    }

    private val episodeChecks = listOf(
        Regex("\\bS(\\d{1,2})E(\\d{1,3})\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(\\d{1,2})x(\\d{1,3})\\b", RegexOption.IGNORE_CASE),
    )
    private val seasonOnlyChecks = listOf(
        Regex("\\bS(\\d{1,2})\\b", RegexOption.IGNORE_CASE),
        Regex("\\bseason\\s*\\.?\\s*(\\d{1,2})\\b", RegexOption.IGNORE_CASE),
    )
    private val episodeOnlyChecks = listOf(
        Regex("\\b(?:episode|ep)\\s*\\.?\\s*(\\d{1,3})\\b", RegexOption.IGNORE_CASE),
        Regex("\\bE(\\d{2,3})\\b", RegexOption.IGNORE_CASE),
    )

    /**
     * Whether a release names this episode: 1000 for the right SxxEyy, 100 for the right
     * season alone, 500 for the right episode alone, negative for a wrong one, 0 when it names
     * neither (or this isn't an episode).
     */
    fun episodeMatchScore(subtitleRelease: String, season: Int?, episode: Int?): Int {
        if (season == null || episode == null || episode == 0) return 0
        for (pattern in episodeChecks) {
            val m = pattern.find(subtitleRelease) ?: continue
            val s = m.groupValues[1].toInt()
            val e = m.groupValues[2].toInt()
            return if (s == season && e == episode) 1000 else -1000
        }
        for (pattern in seasonOnlyChecks) {
            val m = pattern.find(subtitleRelease) ?: continue
            return if (m.groupValues[1].toInt() == season) 100 else -1000
        }
        for (pattern in episodeOnlyChecks) {
            val m = pattern.find(subtitleRelease) ?: continue
            return if (m.groupValues[1].toInt() == episode) 500 else -500
        }
        return 0
    }

    /**
     * The best result: the right episode first, then the closest release name, then the most
     * downloads. Without a release name, the right episode, then the most downloads.
     */
    fun selectBest(
        results: List<SubtitleSearchResult>,
        mediaReleaseName: String?,
        season: Int?,
        episode: Int?,
    ): SubtitleSearchResult {
        require(results.isNotEmpty()) { "No subtitle results to select from" }
        if (mediaReleaseName.isNullOrEmpty()) {
            return results.fold(results[0]) { best, current ->
                val bestScore = episodeMatchScore(best.release, season, episode)
                val currentScore = episodeMatchScore(current.release, season, episode)
                when {
                    currentScore != bestScore -> if (currentScore > bestScore) current else best
                    current.downloads > best.downloads -> current
                    else -> best
                }
            }
        }
        return results.sortedWith(
            compareByDescending<SubtitleSearchResult> { episodeMatchScore(it.release, season, episode) }
                .thenByDescending { releaseSimilarity(mediaReleaseName, it.release) }
                .thenByDescending { it.downloads },
        ).first()
    }

    /** The manual search's order: closest release name first, then the most downloads. */
    fun sortForDisplay(results: List<SubtitleSearchResult>, mediaReleaseName: String?): List<SubtitleSearchResult> {
        if (mediaReleaseName.isNullOrEmpty() || results.isEmpty()) return results
        return results.sortedWith(
            compareByDescending<SubtitleSearchResult> { releaseSimilarity(mediaReleaseName, it.release) }
                .thenByDescending { it.downloads },
        )
    }
}
