package com.strmr.tv.taste

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.Title
import com.strmr.tv.ratings.TitleRatingChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject

@Serializable
data class TasteSetupReply(
    val status: String,
    val source: String = "",
    val titles: List<Title> = emptyList(),
    val available: Boolean = false,
    val canUseHistory: Boolean = false,
    val mediaTypes: List<String> = listOf("movie", "series"),
)
@Serializable
private data class TasteCandidate(val title: Title)
@Serializable
private data class TasteCandidatePage(val items: List<TasteCandidate> = emptyList())

interface TasteSetupGateway {
    fun isCurrent(): Boolean
    suspend fun read(): TasteSetupReply
    suspend fun candidates(query: String, movie: Boolean): List<Title>
    suspend fun save(action: String, titles: List<Title>): TasteSetupReply
}

/** Captured by the originating page/menu, before any asynchronous work. */
class ServerTasteSetupGateway : TasteSetupGateway {
    private val profile = ApiClient.profileId
    private val server = ApiClient.baseUrl
    private val account = ApiClient.session?.accountId
    private val remote = ApiClient.remoteCredential
    private val path = "${ApiClient.profilePath}/taste-onboarding"
    override fun isCurrent() = profile == ApiClient.profileId && server == ApiClient.baseUrl && account == ApiClient.session?.accountId && remote == ApiClient.remoteCredential
    private fun checkCurrent() { if (!isCurrent()) throw CancellationException("Taste setup context changed") }
    override suspend fun read(): TasteSetupReply { checkCurrent(); return ApiClient.send("GET", path, timeoutMs = 5_000) }
    override suspend fun candidates(query: String, movie: Boolean): List<Title> {
        checkCurrent()
        val params = mapOf("kind" to if (movie) "movie" else "series", "q" to query)
        return ApiClient.send<TasteCandidatePage>("GET", "$path/candidates", params, timeoutMs = 8_000).items.map { it.title }
    }
    override suspend fun save(action: String, titles: List<Title>): TasteSetupReply {
        checkCurrent()
        return ApiClient.send("PUT", path, body = tasteSetupBody(action, titles), timeoutMs = 8_000)
    }
}

fun tasteSetupBody(action: String, titles: List<Title>) = buildJsonObject {
    require(action in setOf("save", "skip", "history"))
    require(titles.size <= 40 && (action == "save" || titles.isEmpty()))
    put("action", action)
    if (action == "save") putJsonArray("titles") {
        titles.forEach { title ->
            require(validTasteTitle(title))
            addJsonObject { put("titleId", title.id); put("mediaType", if (title.isMovie) "movie" else "series") }
        }
    }
}

fun validTasteTitle(title: Title) = title.id.isNotBlank() && title.id.length <= 128 && title.mediaType in setOf("movie", "series", "tv")
fun sameTasteTitle(a: Title, b: Title): Boolean = a.isMovie == b.isMovie && (
    a.id == b.id || a.tmdbId?.takeIf { it > 0 }?.let { it == b.tmdbId } == true ||
    a.tvdbId?.takeIf { it > 0 }?.let { it == b.tvdbId } == true ||
    a.imdbId?.takeIf { it.isNotBlank() }?.let { it == b.imdbId } == true
)

data class TasteSetupState(
    val loading: Boolean = true,
    val intro: Boolean = false,
    val available: Boolean = false,
    val canUseHistory: Boolean = false,
    val mediaTypes: List<String> = emptyList(),
    val selected: List<Title> = emptyList(),
    val candidates: List<Title> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
)

/** Staged choices stay local until Done; Cancel never changes the profile.
 * Skip remains separate from clearing existing starting interests. */
class TasteOnboardingEditor(
    private val gateway: TasteSetupGateway,
    private val initial: TasteSetupReply? = null,
    private val intro: Boolean = false,
    private val onSaved: () -> Unit = TitleRatingChanges::saved,
) {
    private val _state = MutableStateFlow(TasteSetupState())
    val state = _state.asStateFlow()
    private var closed = false
    private var busy = false
    private var initialUsed = false
    private var searchGeneration = 0
    fun isCurrent() = !closed && gateway.isCurrent()
    fun close() { closed = true; searchGeneration++ }
    private fun checkCurrent() { if (!isCurrent()) throw CancellationException("Taste setup context changed") }
    private fun validate(reply: TasteSetupReply) {
        check(reply.status in setOf("new", "skipped", "completed") && reply.source in setOf("", "manual", "history") && reply.titles.size <= 40 && reply.titles.all(::validTasteTitle) && reply.mediaTypes.all { it in setOf("movie", "series") } && (!reply.available || reply.mediaTypes.isNotEmpty())) { "Invalid taste setup response" }
    }
    suspend fun load() {
        if (busy) return
        checkCurrent(); busy = true
        _state.value = TasteSetupState()
        try {
            val reply = if (!initialUsed && initial != null) initial else gateway.read(); initialUsed = true; checkCurrent(); validate(reply)
            _state.value = TasteSetupState(loading = false, intro = intro && reply.status == "new", available = reply.available, canUseHistory = reply.canUseHistory, mediaTypes = reply.mediaTypes, selected = reply.titles)
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) { checkCurrent(); _state.value = TasteSetupState(loading = false, error = tasteFailure(error))
        } finally { busy = false }
    }
    fun chooseFavorites() { if (isCurrent() && !_state.value.saving && _state.value.available) _state.value = _state.value.copy(intro = false, error = null) }
    fun selected(title: Title) = _state.value.selected.any { sameTasteTitle(it, title) }
    fun toggle(title: Title) {
        if (!isCurrent()) return
        require(validTasteTitle(title))
        if (_state.value.loading || _state.value.saving || !_state.value.available) return
        val current = _state.value.selected
        if (selected(title)) _state.value = _state.value.copy(selected = current.filterNot { sameTasteTitle(it, title) }, error = null)
        else if (current.size < 40) _state.value = _state.value.copy(selected = current + title, error = null)
        else _state.value = _state.value.copy(error = "Choose up to 40 titles.")
    }
    fun clearSelection() { if (!isCurrent()) return; if (!_state.value.saving && _state.value.available) _state.value = _state.value.copy(selected = emptyList(), error = null) }
    suspend fun search(query: String, movie: Boolean) {
        checkCurrent()
        if (!_state.value.available || _state.value.intro || _state.value.finished || (if (movie) "movie" else "series") !in _state.value.mediaTypes) return
        val generation = ++searchGeneration
        _state.value = _state.value.copy(searching = true, searchError = null)
        try {
            val titles = gateway.candidates(query.trim(), movie); checkCurrent()
            if (generation != searchGeneration || _state.value.finished) return
            val candidates = titles.filter { validTasteTitle(it) && it.isMovie == movie }.distinctBy { (if (it.isMovie) "movie" else "series") + ":" + it.id }.take(24)
            _state.value = _state.value.copy(candidates = candidates, searching = false, searchError = null)
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            checkCurrent(); if (generation == searchGeneration && !_state.value.finished) _state.value = _state.value.copy(searching = false, searchError = "Could not load titles. Try searching again.")
        }
    }
    suspend fun finish(action: String = "save") {
        checkCurrent(); require(action in setOf("save", "skip", "history"))
        if (busy || _state.value.loading || !_state.value.available || _state.value.finished) return
        if (action == "history" && !_state.value.canUseHistory) return
        busy = true
        val selected = _state.value.selected
        _state.value = _state.value.copy(saving = true, error = null)
        try {
            val reply = gateway.save(action, if (action == "save") selected else emptyList()); checkCurrent(); validate(reply)
            check(reply.status == if (action == "skip") "skipped" else "completed") { "Taste setup was not saved" }
            _state.value = _state.value.copy(selected = reply.titles, saving = false, finished = true)
            onSaved()
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) { checkCurrent(); _state.value = _state.value.copy(saving = false, error = tasteFailure(error))
        } finally { busy = false }
    }
}

private fun tasteFailure(error: Exception): String = when ((error as? ApiError)?.statusCode) {
    404, 405, 501 -> "Starting interests are unavailable on this server."
    403 -> "This profile cannot change these starting interests."
    409 -> "Search for the selected titles again, or choose favorites instead of history."
    else -> "Could not update your starting interests. Try again later."
}
