package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SubtitleServerSettings
import com.strmr.tv.api.searchSubtitles
import kotlinx.coroutines.CancellationException

/**
 * One automatic subtitle search (the Expo player's `performAutoSubtitleSearch`), in order:
 * 1. a non-English language with translation on and an English embedded track: extract that
 *    track to WebVTT on the server, translate it, show it once it has a few cues and keep
 *    refreshing it while the server translates more;
 * 2. no provider configured: stop;
 * 3. search the providers; nothing found (or an error): the English embedded track for a
 *    non-English language, else nothing;
 * 4. download the best match ([SubtitleMatching.selectBest]) and show it.
 * Runs in [OnlineSubtitles]' job, so a new run or leaving the player cancels it.
 */
class AutoSubtitleRun(
    private val online: OnlineSubtitles,
    private val controller: PlayerController,
    private val runId: String,
    private val language: String,
    private val server: SubtitleServerSettings,
) {
    private fun log(event: String, details: String = "") = online.log(OnlineSubtitles.TAG, "run=$runId $event", details)

    suspend fun run() {
        log("start", "language=$language provider=${server.providerConfigured} translation=${server.translationEnabled} " +
            "release=${online.releaseName} streams=${controller.session.subtitleStreams.size}")
        online.setStatus("Searching for subtitles…")
        try {
            if (!SubtitleLanguages.isEnglish(language) && server.translationEnabled && translate()) return
            if (!server.providerConfigured) {
                log("search_skip_missing_credentials")
                online.setStatus("No subtitle provider configured", 3_000)
                return
            }
            val q = online.query
            val results = ApiClient.searchSubtitles(q, language)
            log("search_results", "count=${results.size}")
            if (results.isEmpty()) {
                if (!useEnglishEmbedded("fallback_to_english_embedded")) {
                    log("no_results")
                    online.setStatus("No subtitles found", 3_000)
                }
                return
            }
            online.setStatus("Downloading subtitles…")
            val best = SubtitleMatching.selectBest(results, online.releaseName, q.season, q.episode)
            log("selected_online_match", "provider=${best.provider} id=${best.id} release=${best.release}")
            val cues = online.download(best, language, OnlineSubtitles.TAG, "external-download")
            if (cues == null) {
                log("external_no_cues")
                online.setStatus("No usable subtitles found", 3_000)
                return
            }
            controller.showExternal(ExternalTrack("Online: ${SubtitleLanguages.displayName(language)}", best.release.ifEmpty { best.provider }, cues))
            log("external_ready", "cues=${cues.size}")
            online.setStatus("Subtitles ready", 2_000)
        } catch (e: CancellationException) {
            log("cancelled")
            online.setStatus(null)
            throw e
        } catch (e: Exception) {
            log("search_error", "error=${e.message}")
            if (useEnglishEmbedded("error_fallback_to_english_embedded")) return
            log("failed")
            online.setStatus("Subtitle search failed", 3_000)
        }
    }

    /** Step 1; true once a translation is on screen. */
    private suspend fun translate(): Boolean {
        val english = englishSourceTrack()
        log("english_track_resolution", "track=$english")
        if (english == null) return false
        online.setStatus("Extracting & translating subtitles…")
        return SubtitleTranslation(online, controller, OnlineSubtitles.TAG, "run=$runId english_").run(english, "en", language)
    }

    /**
     * The English track to translate, as `/api/video/subtitles/start` numbers it (position
     * among the file's subtitle streams): from the server's probe when it sent one, else the
     * player's own track list. Text tracks only, not forced; SDH preferred, signs avoided.
     */
    private fun englishSourceTrack(): Int? {
        val streams = controller.session.subtitleStreams
        if (streams.isNotEmpty()) {
            val english = streams.filter {
                SubtitleLanguages.canonical(it.language) == "eng" && !it.forced && !it.isBitmap &&
                    !it.title.contains("forced", ignoreCase = true)
            }
            // The probe's `index` is the container number; the extractor counts positions.
            return preferred(english) { it.title }?.let { streams.indexOf(it) }
        }
        val text = OnlineSubtitles.textTracks(controller.tracks.value)
        val english = text.filter {
            SubtitleLanguages.canonical(it.format.language) == "eng" && !it.format.isForcedSubtitle && !it.format.isBitmapSubtitle
        }
        return preferred(english) { it.format.label.orEmpty() }?.let { text.indexOf(it) }
    }

    private fun <T> preferred(tracks: List<T>, title: (T) -> String): T? =
        tracks.firstOrNull { title(it).contains("sdh", ignoreCase = true) }
            ?: tracks.firstOrNull { !title(it).contains("sign", ignoreCase = true) }
            ?: tracks.firstOrNull()

    /** For a non-English language, the English embedded track ("Using English subtitles"); false if none. */
    private fun useEnglishEmbedded(event: String): Boolean {
        if (SubtitleLanguages.isEnglish(language)) return false
        val track = OnlineSubtitles.textTracks(controller.tracks.value).firstOrNull {
            it.supported && SubtitleLanguages.canonical(it.format.language) == "eng" && !it.format.isForcedSubtitle
        } ?: return false
        controller.select(track, byUser = false)
        log(event, "track=${track.typeIndex}")
        online.setStatus("Using English subtitles", 2_000)
        return true
    }
}
