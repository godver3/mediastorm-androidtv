package com.strmr.tv.home

import com.strmr.tv.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object SourceRecheckEvents {
    private val _generation = MutableStateFlow(0L)
    val generation: StateFlow<Long> = _generation.asStateFlow()
    fun requested() { _generation.update { it + 1 } }
}
interface SourceRecheckGateway {
    fun isCurrent(): Boolean
    suspend fun request(body: SourceRecheckRequest): SourceRecheckResponse
}
class ServerSourceRecheckGateway : SourceRecheckGateway {
    private val profile = ApiClient.profileId
    private val server = ApiClient.baseUrl
    private val account = ApiClient.session?.accountId
    private val remote = ApiClient.remoteCredential
    private val path = ApiClient.profilePath
    override fun isCurrent() = profile == ApiClient.profileId && server == ApiClient.baseUrl && account == ApiClient.session?.accountId && remote == ApiClient.remoteCredential
    override suspend fun request(body: SourceRecheckRequest): SourceRecheckResponse {
        if (!isCurrent()) throw CancellationException("Source recheck context changed")
        return ApiClient.send("POST", "$path/source-availability/recheck", body = body.toSourceRecheckBody(), timeoutMs = 5_000)
    }
}

/** Explicit checks retain server language, quality, rights, budget and cooldown policy. */
suspend fun requestSourceRecheck(gateway: SourceRecheckGateway, target: DetailsTarget, episode: Episode? = null, onAccepted: () -> Unit = SourceRecheckEvents::requested) {
    require(target.titleId.isNotBlank() && !target.fromLibrary) { "A catalogue title is required" }
    require(episode == null || !target.isMovie && episode.id.isNotBlank() && episode.seasonNumber >= 0 && episode.episodeNumber > 0 && episode.numbering?.seriesId?.isNotBlank() == true) { "Episode numbering is unavailable" }
    if (!gateway.isCurrent()) throw CancellationException("Source recheck context changed")
    val body = SourceRecheckRequest(target.titleId, if (target.isMovie) "movie" else "series", episode?.numbering, episode?.let { EpisodeSourceCoordinate(it.id,it.seasonNumber,it.episodeNumber) })
    val reply = gateway.request(body)
    if (!gateway.isCurrent()) throw CancellationException("Source recheck context changed")
    check(reply.status == "queued") { "Source recheck was not accepted" }
    onAccepted()
}
