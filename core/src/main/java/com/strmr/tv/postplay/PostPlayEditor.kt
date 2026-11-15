package com.strmr.tv.postplay

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.ListedTitle
import com.strmr.tv.api.Title
import com.strmr.tv.library.ProfileDataContext
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.ratings.PersonalRating
import com.strmr.tv.ratings.ServerTitleRatingGateway
import com.strmr.tv.ratings.TitleRatingChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class PostPlayReply(val title: Title, val rating: String, val ratingScope: String, val completion: String)
@Serializable
data class PostPlaySuggestions(val items: List<ListedTitle> = emptyList(), val source: String, val rating: String, val ratingScope: String)

interface PostPlayGateway {
    fun isCurrent(): Boolean
    suspend fun context(): PostPlayReply
    suspend fun suggestions(): PostPlaySuggestions
    suspend fun rate(target: DetailsTarget, value: PersonalRating): String
    suspend fun saveToList(target: DetailsTarget)
}

class ServerPostPlayGateway(private val target: DetailsTarget, private val itemId: () -> String?) : PostPlayGateway {
    private val epoch = ProfileDataContext.current
    private val path = "${ApiClient.profilePath}/post-play/${if (target.isMovie) "movie" else "series"}/${ApiClient.segment(target.titleId)}"
    private val query get() = itemId()?.let { mapOf("itemId" to it) }.orEmpty()
    private val ratings = ServerTitleRatingGateway(promptOnLock = false)
    override fun isCurrent() = epoch == ProfileDataContext.current
    private fun checkCurrent() { if (!isCurrent()) throw CancellationException("Post-play profile changed") }
    override suspend fun context(): PostPlayReply {
        checkCurrent(); val reply = ApiClient.send<PostPlayReply>("GET", path, query, promptOnLock = false, timeoutMs = 5_000, expectedContext = epoch); checkCurrent(); return reply
    }
    override suspend fun suggestions(): PostPlaySuggestions {
        checkCurrent(); val reply = ApiClient.send<PostPlaySuggestions>("GET", "$path/recommendations", query, promptOnLock = false, timeoutMs = 5_000, expectedContext = epoch); checkCurrent(); return reply
    }
    override suspend fun rate(target: DetailsTarget, value: PersonalRating): String {
        checkCurrent(); val reply = ratings.write(target,value); checkCurrent(); return reply.rating
    }
    override suspend fun saveToList(target: DetailsTarget) { checkCurrent(); WatchlistStore.add(target); checkCurrent() }
}

data class PostPlayState(
    val title: Title? = null,
    val rating: PersonalRating? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val questionVisible: Boolean = false,
    val expandedRating: Boolean = false,
    val suggestions: List<Title> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val suggestionsError: String? = null,
    val suggestionsSource: String? = null,
    val listSaving: Set<String> = emptySet(),
    val savedToList: Set<String> = emptySet(),
    val listError: String? = null,
)

/** Temporary end-view state. Every preference and list write uses its existing store. */
class PostPlayEditor(
    private val target: DetailsTarget,
    private val gateway: PostPlayGateway,
    private val onRated: () -> Unit = TitleRatingChanges::saved,
    private val navigate: (DetailsTarget, Boolean) -> Unit = { _, _ -> },
) {
    private val _state = MutableStateFlow(PostPlayState())
    val state: StateFlow<PostPlayState> = _state.asStateFlow()
    private var closed = false
    private var busy = false
    private var navigating = false
    private var generation = 0L
    private var questionDismissed = false
    private val kind get() = if (target.isMovie) "movie" else "series"
    fun close() { closed = true; generation++ }
    private fun current() = !closed && gateway.isCurrent()
    private fun checkCurrent() { if (!current()) throw CancellationException("Post-play profile changed") }

    suspend fun load() {
        if (busy) return
        checkCurrent(); busy = true; generation++
        _state.value = PostPlayState(savedToList = _state.value.savedToList, listSaving = _state.value.listSaving)
        try {
            val reply = gateway.context(); checkCurrent()
            check(reply.title.id == target.titleId && reply.ratingScope == kind && reply.title.mediaType == kind &&
                reply.completion == if (target.isMovie) "movie" else "episode") { "Unexpected completed title" }
            val rating = PersonalRating.from(reply.rating)
            _state.value = PostPlayState(title = reply.title, rating = rating, loading = false, questionVisible = rating == PersonalRating.NONE && !questionDismissed, savedToList = _state.value.savedToList, listSaving = _state.value.listSaving)
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            checkCurrent(); _state.value = PostPlayState(loading = false, error = postPlayFailure(error,"Could not load this completed title. Retry or close."))
        } finally { busy = false }
        if (_state.value.title != null) refreshSuggestions()
    }

    suspend fun refreshSuggestions() {
        if (busy || _state.value.title == null) return
        checkCurrent(); val captured = ++generation
        val expected = _state.value.rating
        _state.value = _state.value.copy(suggestionsLoading = true, suggestionsError = null)
        try {
            val reply = gateway.suggestions(); checkCurrent()
            if (captured != generation) return
            check(reply.ratingScope == kind && PersonalRating.from(reply.rating) == expected && reply.source in setOf("personalized","saved_choices","unavailable")) { "Stale recommendation inputs" }
            check(reply.source != "unavailable" || reply.items.isEmpty()) { "Unexpected unavailable candidates" }
            val counts = mutableMapOf<String,Int>()
            val titles = reply.items.map { it.title }.filter { it.id.isNotBlank() && it.id.length <= 128 && it.mediaType in setOf("movie", "series") && (it.id != target.titleId || it.isMovie != target.isMovie) && it.personalRating != "dislike" }.distinctBy { it.mediaType to it.id }.filter { title ->
                val count = (counts[title.mediaType] ?: 0) + 1; counts[title.mediaType] = count; count <= 6
            }.take(12)
            _state.value = _state.value.copy(suggestions = titles, suggestionsLoading = false, suggestionsSource = reply.source)
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) {
            checkCurrent(); if (captured != generation) return
            _state.value = _state.value.copy(suggestionsLoading = false, suggestionsError = "Suggestions are unavailable. Retry when ready.")
        }
    }

    suspend fun rate(value: PersonalRating) {
        if (busy || _state.value.title == null || _state.value.rating == null) return
        checkCurrent()
        if (value == _state.value.rating && _state.value.error == null) return
        busy = true; val previous = _state.value
        _state.value = previous.copy(saving = true, error = null)
        var saved = false
        try {
            val reply = gateway.rate(DetailsTarget.from(requireNotNull(previous.title)),value); checkCurrent()
            check(PersonalRating.from(reply) == value) { "Unexpected rating confirmation" }
            generation++; questionDismissed = true
            _state.value = _state.value.copy(rating = value, saving = false, error = null, questionVisible = false, expandedRating = false,
                suggestions = emptyList(), suggestionsLoading = true, suggestionsError = null)
            onRated(); saved = true
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            checkCurrent(); _state.value = _state.value.copy(rating = previous.rating, saving = false, error = postPlayFailure(error,"Could not save your rating. Select it again to retry."))
        } finally { busy = false }
        if (saved) refreshSuggestions()
    }

    fun skipRating() { if (current() && !busy) { questionDismissed = true; _state.value = _state.value.copy(questionVisible = false, expandedRating = false) } }
    fun editRating() { if (current() && !busy && _state.value.title != null) { questionDismissed = false; _state.value = _state.value.copy(questionVisible = true, expandedRating = true) } }

    suspend fun saveToList(title: Title) {
        val key = title.mediaType + ":" + title.id
        if (!current() || busy || !hasSuggestion(title) || key in _state.value.listSaving || key in _state.value.savedToList) return
        _state.value = _state.value.copy(listSaving = _state.value.listSaving + key, listError = null)
        try {
            gateway.saveToList(DetailsTarget.from(title)); checkCurrent()
            _state.value = _state.value.copy(listSaving = _state.value.listSaving - key, savedToList = _state.value.savedToList + key)
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) {
            checkCurrent(); _state.value = _state.value.copy(listSaving = _state.value.listSaving - key, listError = "Could not save to My List. Try again.")
        }
    }

    private fun hasSuggestion(title: Title) = _state.value.suggestions.any { it.id == title.id && it.mediaType == title.mediaType }
    fun select(title: Title, play: Boolean): Boolean {
        if (!current() || busy || navigating || !hasSuggestion(title)) return false
        navigating = true; navigate(DetailsTarget.from(title),play); return true
    }
}

private fun postPlayFailure(error: Exception, fallback: String): String =
    if ((error as? ApiError)?.statusCode == 403) "Unlock this profile in the app to rate this title." else fallback
