package com.strmr.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SubtitleQuery
import com.strmr.tv.api.SubtitleSearchResult
import com.strmr.tv.api.SubtitleServerSettings
import com.strmr.tv.api.downloadSubtitle
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.api.searchSubtitles
import com.strmr.tv.api.subtitleServerSettings
import com.strmr.tv.app.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Online subtitles for one playback, as the Expo player has them: the automatic search when
 * the stream has no subtitles in the profile's language ([AutoSubtitleRun]: a translation of
 * the English track, else the best provider match, else the English track), the manual
 * Search Online page, and short status messages. Never for live TV or trailers.
 */
class OnlineSubtitles(private val controller: PlayerController, private val scope: CoroutineScope) {
    private val session = controller.session
    val available: Boolean = !session.isLive && session.reportsProgress && session.streamPath != null
    val releaseName: String? = session.releaseName
    val query = session.request.let { r ->
        SubtitleQuery(r.title.imdbId, r.title.name, releaseName, r.title.year, r.episode?.season, r.episode?.episode)
    }
    /** "Searching for subtitles…", "Subtitles ready"…; cleared after a few seconds. */
    val status = MutableStateFlow<String?>(null)

    private var checked = false
    private var translateJob: Job? = null
    /**
     * The server's translation switch (`subtitles.enableTranslatedSubs`), null until read:
     * Translate… shows only while it is on. The device's Subtitle Translation override governs
     * the automatic flow only; Translate… is an explicit choice.
     */
    val serverTranslation = MutableStateFlow<Boolean?>(null)
    private var autoJob: Job? = null
    private var runs = 0
    private var statusJob: Job? = null
    private var serverSettings: SubtitleServerSettings? = null
    private var playback: com.strmr.tv.api.PlaybackSettings? = null

    init {
        // Read the server's switches up front, so the Subtitles menu knows whether to offer Translate….
        if (available) scope.launch { runCatching { server() } }
    }

    fun log(area: String, event: String, details: String = "") =
        AppLog.i(area, if (details.isEmpty()) event else "$event $details")

    fun setStatus(message: String?, clearAfterMs: Long = 0) {
        statusJob?.cancel()
        status.value = message
        if (message != null && clearAfterMs > 0) {
            statusJob = scope.launch {
                delay(clearAfterMs)
                status.value = null
            }
        }
    }

    /**
     * The server's subtitle settings with this device's Subtitle Translation override: the
     * server's switch is a kill switch, so the device can only turn translation off.
     */
    suspend fun server(): SubtitleServerSettings {
        val s = serverSettings ?: ApiClient.subtitleServerSettings().also { serverSettings = it; serverTranslation.value = it.translationEnabled }
        return s.copy(translationEnabled = s.translationEnabled && TrackOverrides.translation != false)
    }

    private suspend fun playbackSettings() = playback ?: session.playback ?: ApiClient.playbackSettings().also { playback = it }

    /**
     * The subtitle language and mode in effect: this device's overrides, else the server's
     * track choice, else the profile settings.
     */
    private suspend fun preference(): Pair<String?, String?> {
        val e = controller.effectiveTracks
        if (session.tracks != null || session.playback != null) return e.subtitleLanguage to e.subtitleMode
        val p = playbackSettings()
        return (TrackOverrides.subtitleLanguage ?: p.preferredSubtitleLanguage) to (TrackOverrides.subtitleMode ?: p.preferredSubtitleMode)
    }

    /**
     * Once per playback, after the server's track choice is applied: starts the automatic
     * search when the mode is `on`, a language is set, no embedded track is in that language
     * (the server's pick, if any, is a fallback and stays on screen meanwhile) and there is a
     * provider or a translation to try.
     */
    fun tracksReady(tracks: Tracks, pick: PlayerTrack?) {
        if (checked) return
        checked = true
        if (!available) {
            log(TAG, "trigger_skip_live_tv", "live=${session.isLive}")
            return
        }
        autoJob = scope.launch {
            val (language, mode) = preference()
            log(TAG, "trigger_check", "mode=$mode language=$language pick=${pick?.format?.language}")
            if (mode != "on") return@launch log(TAG, "trigger_skip_mode_off", "mode=$mode")
            if (language == null) return@launch log(TAG, "trigger_skip_no_language")
            val code = SubtitleLanguages.canonical(language) ?: language
            val text = textTracks(tracks)
            val matches = pick?.let { TrackPreferences.sameLanguage(it.format.language, code) } == true ||
                text.any { !it.format.isForcedSubtitle && TrackPreferences.sameLanguage(it.format.language, code) }
            if (matches) return@launch log(TAG, "trigger_skip_embedded_match", "language=$language")
            if (text.isNotEmpty() && text.none { it.format.knownLanguage != null }) {
                return@launch log(TAG, "trigger_skip_unknown_embedded_languages", "tracks=${text.size}")
            }
            val server = server()
            val translationEligible = server.translationEnabled && !SubtitleLanguages.isEnglish(language)
            if (!server.providerConfigured && !translationEligible) {
                return@launch log(TAG, "trigger_skip_missing_credentials", "language=$language")
            }
            runs += 1
            val id = "auto-$runs-${System.currentTimeMillis()}"
            log(TAG, "trigger_fire", "run=$id language=$language tracks=${text.size} fallback=${pick?.format?.language}")
            AutoSubtitleRun(this@OnlineSubtitles, controller, id, language, server).run()
        }
    }

    /** The user picked a track (or Off) themselves: a running automatic search stops. */
    fun userChose() {
        if (autoJob?.isActive == true) log(TAG, "cancelled_by_user")
        autoJob?.cancel()
        translateJob?.cancel()
    }

    // Manual search

    /** The search page's languages and the one it opens on: the profile's, else the first allowed. */
    suspend fun searchLanguages(): Pair<List<SubtitleLanguages.Option>, String> {
        val options = SubtitleLanguages.allowed(playbackSettings().allowedTrackLanguages).ifEmpty { SubtitleLanguages.options }
        val preferred = preference().first?.let { SubtitleLanguages.searchCode(it) }
        return options to (options.firstOrNull { it.code == preferred }?.code ?: options.first().code)
    }

    suspend fun search(language: String): List<SubtitleSearchResult> {
        log(SEARCH_TAG, "search", "language=$language")
        val results = ApiClient.searchSubtitles(query, language)
        log(SEARCH_TAG, "search_results", "language=$language count=${results.size}")
        return SubtitleMatching.sortForDisplay(results, releaseName)
    }

    /** Downloads a picked result and shows it as "Online: <language>". */
    fun use(result: SubtitleSearchResult, language: String) {
        userChose()
        log(SEARCH_TAG, "selected", "provider=${result.provider} release=${result.release}")
        setStatus("Downloading subtitles…")
        scope.launch {
            val cues = download(result, language, SEARCH_TAG, "manual-download")
            if (cues == null) {
                setStatus("No usable subtitles found", 3_000)
                return@launch
            }
            controller.showExternal(ExternalTrack("Online: ${SubtitleLanguages.displayName(language)}", result.release.ifEmpty { result.provider }, cues))
            setStatus("Subtitles ready", 2_000)
        }
    }

    /** A result's cues, once its WebVTT has at least one (up to 8 tries 200 ms apart); null if none. */
    suspend fun download(result: SubtitleSearchResult, language: String, area: String, phase: String): List<VttCue>? {
        val body = waitForCues(area, phase, attempts = 8, delayMs = 200) { ApiClient.downloadSubtitle(result, query, language) }
        return body?.let { parse(it) }?.takeIf { it.isNotEmpty() }
    }

    /**
     * Fetches until the WebVTT has `minCues` cues or `minBytes` bytes (and at least one cue),
     * as the Expo player's `waitForVttCues`: a failed fetch gives up; when the tries run out,
     * any body with cues is accepted if `acceptPartial`.
     */
    suspend fun waitForCues(
        area: String, phase: String, attempts: Int, delayMs: Long,
        minCues: Int = 1, minBytes: Int = 0, acceptPartial: Boolean = true,
        fetch: suspend () -> String,
    ): String? {
        var best: String? = null
        var bestCount = 0
        for (attempt in 1..attempts) {
            val body = try {
                fetch()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log(area, "probe_fetch_error", "phase=$phase attempt=$attempt error=${e.message}")
                return null
            }
            val count = WebVtt.countCues(body)
            log(area, "probe_attempt", "phase=$phase attempt=$attempt/$attempts cues=$count bytes=${body.length}")
            if (count > 0 && (count >= minCues || body.length >= minBytes)) {
                log(area, "probe_ready", "phase=$phase attempt=$attempt cues=$count")
                return body
            }
            if (count >= bestCount && count > 0) { best = body; bestCount = count }
            if (attempt < attempts) delay(delayMs)
        }
        log(area, "probe_exhausted", "phase=$phase maxCues=$bestCount")
        return if (acceptPartial) best else null
    }

    suspend fun parse(body: String): List<VttCue> = withContext(Dispatchers.Default) { WebVtt.parse(body) }

    // Translate…

    /** The tracks Translate… offers: the stream's text subtitles (bitmap ones can't be extracted to text). */
    fun translatableTracks(tracks: Tracks) =
        textTracks(tracks).filter { it.supported && !it.format.isBitmapSubtitle && it.format.subtitleCandidate().codec != "dvd_subtitle" }

    /** Translates `track` to `target` (a search language code) and shows it, as the Expo player's Translate. */
    fun translate(track: PlayerTrack, target: String) {
        userChose()
        translateJob?.cancel()
        val streams = session.subtitleStreams
        val id = track.format.id?.toIntOrNull()
        val text = textTracks(controller.tracks.value)
        val index = TranslationSource.extractIndex(id, streams, text.indexOfFirst { it.group == track.group && it.index == track.index })
        val label = listOfNotNull(track.format.menuTitle(""), track.format.label).joinToString(" ")
        val source = TranslationSource.language(TranslationSource.stream(id, streams)?.language?.ifEmpty { null } ?: track.format.knownLanguage, label)
        log(TRANSLATE_TAG, "translate_requested", "track=$index id=$id from=$source to=$target label=$label")
        setStatus("Translating subtitles…")
        translateJob = scope.launch {
            val shown = try {
                SubtitleTranslation(this@OnlineSubtitles, controller, TRANSLATE_TAG, "").run(index, source, target)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TRANSLATE_TAG, "translate_error", "error=${e.message}")
                false
            }
            if (!shown) setStatus("Translation failed", 3_000)
        }
    }

    fun cancel() {
        translateJob?.cancel()
        autoJob?.cancel()
        statusJob?.cancel()
    }

    companion object {
        const val TAG = "AutoSub"
        const val SEARCH_TAG = "SubSearch"
        const val TRANSLATE_TAG = "SubTranslate"

        /** The stream's subtitle tracks, without closed captions. */
        fun textTracks(tracks: Tracks) = tracks.ofType(C.TRACK_TYPE_TEXT).filter { !TrackPreferences.isClosedCaption(it.format) }
    }
}

val Format.isForcedSubtitle: Boolean
    get() = selectionFlags and C.SELECTION_FLAG_FORCED != 0 || label?.contains("forced", ignoreCase = true) == true

val Format.knownLanguage: String?
    get() = language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED && it != "und" }

val Format.isBitmapSubtitle: Boolean
    get() = subtitleMime in setOf("application/pgs", "application/vobsub", "application/dvbsubs")
