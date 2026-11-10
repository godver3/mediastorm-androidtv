package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.startSubtitleExtract
import com.strmr.tv.api.translateSubtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Translating one embedded text track, for the automatic search and for Translate…: the
 * server extracts it to WebVTT (`/api/video/subtitles/start`, from the current position),
 * `/api/subtitles/translate` translates it a little more on every request, and the result is
 * shown as the external track once it has a few cues, then refreshed while it grows.
 */
class SubtitleTranslation(
    private val online: OnlineSubtitles,
    private val controller: PlayerController,
    private val area: String,
    private val prefix: String,
) {
    private fun log(event: String, details: String = "") = online.log(area, "$prefix$event", details)

    /**
     * Translates subtitle stream `track` (its position among the file's subtitle streams) from
     * `sourceLanguage` to `targetLanguage`; true once the translation is on screen.
     */
    suspend fun run(track: Int, sourceLanguage: String, targetLanguage: String): Boolean {
        val path = controller.session.streamPath ?: return false
        val offset = maxOf(0.0, controller.player.currentPosition / 1000.0, controller.request.startPosition ?: 0.0)
        val source = try {
            ApiClient.startSubtitleExtract(path, track, offset).subtitleUrl.ifEmpty { null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("source_extract_failed", "track=$track error=${e.message}")
            null
        }
        if (source == null) {
            log("source_missing", "track=$track")
            return false
        }
        log("source_extracted", "track=$track source=$source offset=$offset from=$sourceLanguage to=$targetLanguage")
        online.setStatus("Translating subtitles…")
        val fetch: suspend () -> String = { ApiClient.translateSubtitle(source, targetLanguage, sourceLanguage) }
        val body = online.waitForCues(area, "translation", attempts = 30, delayMs = 2_000,
                                      minCues = 4, minBytes = 1_200, acceptPartial = true, fetch = fetch)
        val cues = body?.let { online.parse(it) }.orEmpty()
        if (body == null || cues.isEmpty()) {
            log("translation_no_cues")
            return false
        }
        val title = "Translated: ${SubtitleLanguages.displayName(targetLanguage)}"
        controller.showExternal(ExternalTrack(title, "From ${SubtitleLanguages.displayName(sourceLanguage)}", cues))
        log("translation_ready", "cues=${cues.size}")
        online.setStatus("Translated subtitles ready", 2_000)
        keepTranslating(body, cues.size, fetch)
        return true
    }

    /**
     * Re-fetches every 10 s (after a first 10 s) and refreshes the cues while the translation
     * grows; stops after a minute without change, or when another external track replaces it.
     */
    private fun keepTranslating(first: String, firstCount: Int, fetch: suspend () -> String) {
        val shown = controller.external.track.value
        // Its own job, so the poll outlives the run (which ends here) but not the player.
        controller.scope.launch {
            var lastLength = first.length
            var lastCount = firstCount
            var unchanged = 0
            delay(10_000)
            while (unchanged < 6) {
                val current = controller.external.track.value
                if (current == null || current.title != shown?.title || current.detail != shown.detail) return@launch
                val body = runCatching { fetch() }.getOrNull()
                if (body != null) {
                    val count = WebVtt.countCues(body)
                    if (count > lastCount || body.length - lastLength > 50) {
                        log("translation_refresh", "cues=$lastCount->$count bytes=$lastLength->${body.length}")
                        lastCount = count
                        lastLength = body.length
                        unchanged = 0
                        controller.external.updateCues(online.parse(body))
                    } else {
                        unchanged++
                    }
                }
                delay(10_000)
            }
            log("translation_stable", "cues=$lastCount")
        }
    }
}
