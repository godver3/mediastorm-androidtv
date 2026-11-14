package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.PrequeueStatus
import com.strmr.tv.api.ReleaseReportOptions
import com.strmr.tv.api.ReleaseReportReply
import com.strmr.tv.api.cancelReleaseAlternative
import com.strmr.tv.api.prequeueStatus
import com.strmr.tv.api.releaseReportOptions
import com.strmr.tv.api.submitReleaseAction
import com.strmr.tv.api.undoReleaseReport
import com.strmr.tv.app.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** All report I/O is replaceable by an isolated fixture in tests. */
interface ReleaseReportGateway {
    fun isCurrent(): Boolean
    suspend fun options(id: String): ReleaseReportOptions
    suspend fun submit(id: String, reporting: Boolean, body: JsonObject): ReleaseReportReply
    suspend fun status(id: String): PrequeueStatus
    suspend fun cancel(id: String)
    suspend fun undo(id: String)
}

class ServerReleaseReportGateway : ReleaseReportGateway {
    private val profile = ApiClient.profileId
    private val server = ApiClient.baseUrl
    private val account = ApiClient.session?.accountId
    override fun isCurrent() = profile == ApiClient.profileId && server == ApiClient.baseUrl && account == ApiClient.session?.accountId
    private fun checkSession() = check(isCurrent()) { "The active profile or server changed. Reopen playback." }
    override suspend fun options(id: String): ReleaseReportOptions { checkSession(); return ApiClient.releaseReportOptions(id) }
    override suspend fun submit(id: String, reporting: Boolean, body: JsonObject): ReleaseReportReply { checkSession(); return ApiClient.submitReleaseAction(id, reporting, body) }
    override suspend fun status(id: String): PrequeueStatus { checkSession(); return ApiClient.prequeueStatus(id) }
    override suspend fun cancel(id: String) { if (isCurrent()) ApiClient.cancelReleaseAlternative(id) }
    override suspend fun undo(id: String) { checkSession(); ApiClient.undoReleaseReport(id) }
}

enum class ReleaseResume(val label: String) {
    BEGINNING("From the beginning"), CURRENT("At the current position"), REWIND("10 seconds back");
    fun position(currentMs: Long, wrongContent: Boolean, durationSeconds: Double? = null): Long {
        if (wrongContent || this == BEGINNING) return 0
        val offset = (currentMs.coerceAtLeast(0) - if (this == REWIND) 10_000 else 0).coerceAtLeast(0)
        return if (durationSeconds != null && durationSeconds > 0 && offset / 1000.0 >= durationSeconds) 0 else offset
    }
}

enum class ReleaseProblem(val key: String, val label: String) {
    WRONG_TITLE("wrong_title", "Wrong movie or series"), WRONG_EPISODE("wrong_episode", "Wrong episode"),
    LANGUAGE("missing_language", "Wanted audio language missing or mislabeled"), AUDIO("audio_problem", "Audio track is defective"),
    FILE("file_problem", "File is damaged or incomplete"), QUALITY("quality_problem", "Quality is unsuitable for me"),
    DEVICE("device_problem", "Does not play on this device"), PROVIDER("source_unavailable", "Source is temporarily unavailable");
    val wrongContent get() = this == WRONG_TITLE || this == WRONG_EPISODE
    fun scope(options: ReleaseReportOptions): String {
        val owner = if (options.canShare) "All profiles" else "This profile"
        return when (this) {
            WRONG_TITLE, WRONG_EPISODE, FILE -> "$owner, this title or exact episode only. Other package episodes remain available."
            LANGUAGE -> "$owner, ${languageLabel(options.audioLanguage)} audio requests only. Other languages remain available."
            AUDIO -> "$owner, the selected audio track only. Healthy tracks remain available."
            QUALITY -> "This profile only. Other profiles remain unaffected."
            DEVICE -> "This profile on this device only."
            PROVIDER -> "This source route for this profile, for 15 minutes."
        }
    }
}

fun languageLabel(code: String): String = if (code.isBlank()) "Unknown language" else
    runCatching { java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.getDefault()).ifBlank { code } }.getOrDefault(code)

data class ReleaseReportState(
    val options: ReleaseReportOptions? = null,
    val problem: ReleaseProblem? = null,
    val track: Int? = null,
    val resume: ReleaseResume = ReleaseResume.REWIND,
    val busy: Boolean = false,
    val ready: PrequeueStatus? = null,
    val reportId: String? = null,
    val message: String? = null,
)

/** Prepares one replacement while playback continues; adoption is always a separate user action. */
class ReleaseReports(
    private val sourceId: String,
    private val gateway: ReleaseReportGateway,
    private val scope: CoroutineScope,
    private val position: () -> Long,
    private val adopt: (PrequeueStatus, Long, Boolean) -> Unit,
    private val beforeOptions: suspend () -> Unit = {},
    private val clientId: String = Config.clientId,
    private val pollDelayMs: Long = 500,
    private val pollAttempts: Int = 120,
) {
    private val mutable = MutableStateFlow(ReleaseReportState())
    val state: StateFlow<ReleaseReportState> = mutable
    private var job: Job? = null
    private var pending: String? = null
    private var closed = false
    private fun checkCurrent() { check(!closed && gateway.isCurrent()) { "Playback context changed. Reopen this menu." } }

    suspend fun load() {
        mutable.value = mutable.value.copy(busy = true, message = null)
        try {
            beforeOptions(); checkCurrent()
            val options = gateway.options(sourceId); checkCurrent()
            mutable.value = mutable.value.copy(options = options, busy = false)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, message = "Could not load the selected release. This action needs a server with release reporting.") }
    }

    fun choose(problem: ReleaseProblem?) {
        if (!mutable.value.busy && pending == null) mutable.value = mutable.value.copy(problem = problem, track = null, message = null)
    }
    fun track(index: Int) {
        if (!mutable.value.busy && mutable.value.options?.audioTracks?.any { it.index == index } == true) {
            mutable.value = mutable.value.copy(track = index)
        }
    }
    fun resume(mode: ReleaseResume) { mutable.value = mutable.value.copy(resume = mode) }

    fun submit(reporting: Boolean, prepare: Boolean) {
        if (mutable.value.busy || pending != null || closed) return
        val options = mutable.value.options ?: return
        val problem = mutable.value.problem
        if (reporting && (problem == null || (problem == ReleaseProblem.AUDIO && mutable.value.track == null))) return
        val body = buildJsonObject {
            put("sourceVersion", options.sourceVersion)
            if (reporting) { put("reason", problem!!.key); put("audioLanguage", options.audioLanguage) }
            if (reporting && problem == ReleaseProblem.AUDIO) put("audioTrackIndex", mutable.value.track!!)
            put("retry", prepare); put("startOffset", 0); put("skipHLS", true); put("clientId", clientId)
        }
        mutable.value = mutable.value.copy(busy = true, message = if (prepare) "Preparing a suitable alternative. Playback can continue." else "Saving report...")
        job = scope.launch {
            try {
                checkCurrent()
                // Obtain the replacement ID even if Back is pressed while POST is in flight,
                // so cancellation can release the preparation instead of leaving an orphan.
                val reply = withContext(NonCancellable) { withTimeout(12_000) { gateway.submit(sourceId, reporting, body) } }
                pending = reply.replacement?.prequeueId
                if (closed || !gateway.isCurrent()) { cleanup(); return@launch }
                mutable.value = mutable.value.copy(reportId = reply.report?.id ?: mutable.value.reportId)
                if (!prepare) {
                    mutable.value = mutable.value.copy(busy = false, message = "Report saved. Playback can continue.")
                    return@launch
                }
                val id = pending ?: error("No alternative preparation was returned.")
                val ready = withTimeout(90_000) {
                    repeat(pollAttempts) {
                        checkCurrent()
                        val status = gateway.status(id)
                        checkCurrent()
                        when (status.status) {
                            "ready" -> {
                                check(!status.streamPath.isNullOrBlank()) { "Prepared source has no stream." }
                                return@withTimeout status
                            }
                            "failed", "expired" -> error("No suitable alternative was found. Your current source is unchanged.")
                        }
                        delay(pollDelayMs)
                    }
                    error("The alternative took too long. Try another source manually.")
                }
                mutable.value = mutable.value.copy(busy = false, ready = ready, message = "Alternative ready. Choose where to start, then switch.")
            } catch (e: TimeoutCancellationException) {
                cleanup()
                mutable.value = mutable.value.copy(busy = false, message = "Preparation timed out. The current source is unchanged.")
            } catch (e: CancellationException) { cleanup(); throw e }
            catch (e: Exception) {
                cleanup()
                mutable.value = mutable.value.copy(busy = false, message = if (e is ApiError) "The server rejected this action. Reopen the menu to refresh the selection and language." else e.message ?: "Could not prepare another source.")
            }
        }
    }

    fun take() {
        val state = mutable.value
        val ready = state.ready ?: return
        if (closed || !gateway.isCurrent()) {
            mutable.value = state.copy(message = "Playback context changed. Reopen this menu.")
            scope.launch { cleanup() }
            return
        }
        val wrong = state.problem?.wrongContent == true
        val offset = state.resume.position(position(), wrong, ready.duration)
        pending = null // ownership is transferred to playback before the menu is disposed
        closed = true
        adopt(ready, offset, wrong)
    }

    fun undo() {
        val id = mutable.value.reportId ?: return
        if (mutable.value.busy || closed) return
        mutable.value = mutable.value.copy(busy = true)
        job = scope.launch {
            try {
                checkCurrent(); gateway.undo(id); checkCurrent()
                cleanup()
                mutable.value = mutable.value.copy(reportId = null, ready = null, busy = false, message = "Report removed.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, message = "Could not remove the report.") }
        }
    }

    private suspend fun cleanup() {
        val id = pending ?: return
        pending = null
        withContext(NonCancellable) { withTimeoutOrNull(5_000) { runCatching { gateway.cancel(id) } } }
    }
    fun close() {
        if (closed) return
        closed = true
        job?.cancel()
        scope.launch { cleanup() }
    }
}
