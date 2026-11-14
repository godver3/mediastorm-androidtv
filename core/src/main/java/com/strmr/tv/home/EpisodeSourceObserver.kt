package com.strmr.tv.home

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.EpisodeSourceCoordinate
import com.strmr.tv.api.EpisodeSourceDemand
import com.strmr.tv.api.EpisodeSourceResponse
import com.strmr.tv.api.toEpisodeSourceBody
import com.strmr.tv.api.SourceAvailability
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

fun episodeSourceKey(ep: Episode): String = "${ep.numbering?.seriesId}|${ep.numbering?.ordering.orEmpty()}|${ep.seasonNumber}|${ep.episodeNumber}|${ep.id}"

interface EpisodeSourceGateway {
    fun isCurrent(): Boolean
    suspend fun update(view: String, body: EpisodeSourceDemand): EpisodeSourceResponse
}
class ServerEpisodeSourceGateway : EpisodeSourceGateway {
    private val profile = ApiClient.profileId
    private val server = ApiClient.baseUrl
    private val account = ApiClient.session?.accountId
    private val remote = ApiClient.remoteCredential
    private val path = ApiClient.profilePath
    override fun isCurrent() = profile == ApiClient.profileId && server == ApiClient.baseUrl && account == ApiClient.session?.accountId && remote == ApiClient.remoteCredential
    override suspend fun update(view: String, body: EpisodeSourceDemand): EpisodeSourceResponse {
        if (!isCurrent()) throw CancellationException("Episode source context changed")
        return ApiClient.send("PUT", "$path/source-availability/episodes/$view", body = body.toEpisodeSourceBody(), promptOnLock = false, timeoutMs = 5_000)
    }
}

/** One stable view ID and ordered versions survive scroll updates, including late requests. */
class EpisodeSourceObserver(
    private val gateway: EpisodeSourceGateway,
    private val target: DetailsTarget,
    private val viewId: String = UUID.randomUUID().toString(),
) {
    private val lock = Any()
    private var owner = 0L
    private var version = 0L
    private var dirty = false
    private fun claim(): Long = synchronized(lock) { ++owner }
    private fun next(batch: Long): Long? = synchronized(lock) {
        if (owner != batch) null else { dirty = true; ++version }
    }
    private fun owns(batch: Long) = synchronized(lock) { owner == batch }
    private suspend fun clear(batch: Long) {
        val clearVersion = synchronized(lock) { if (owner != batch || !dirty) null else ++version } ?: return
        if (!gateway.isCurrent()) return
        withContext(NonCancellable) {
            withTimeoutOrNull(2_000) { runCatching { gateway.update(viewId, EpisodeSourceDemand(clearVersion)) } }
        }
    }
    fun states(visible: List<Episode>): Flow<Map<String, SourceAvailability>> = flow {
        val batch = claim()
        emit(emptyMap())
        val episodes = visible.filter { it.id.isNotBlank() && it.seasonNumber >= 0 && it.episodeNumber > 0 && it.numbering?.seriesId?.isNotBlank() == true }
            .distinctBy(::episodeSourceKey).take(12)
        val numbering = episodes.firstOrNull()?.numbering
        if (target.isMovie || episodes.isEmpty() || episodes.any { it.numbering != numbering } || !gateway.isCurrent()) { clear(batch); return@flow }
        var pendingPolls = 0
        try {
            while (currentCoroutineContext().isActive && gateway.isCurrent() && owns(batch)) {
                val requestVersion = next(batch) ?: return@flow
                val body = EpisodeSourceDemand(requestVersion, target.titleId, numbering, episodes.map { EpisodeSourceCoordinate(it.id, it.seasonNumber, it.episodeNumber) })
                val reply = gateway.update(viewId, body)
                if (!gateway.isCurrent() || !owns(batch)) return@flow
                if (!reply.enabled) { synchronized(lock) { if (owner == batch) dirty = false }; return@flow }
                if (reply.version != requestVersion) return@flow
                val values = buildMap {
                    for (ep in episodes) {
                        val matches = reply.items.filter { it.episodeId == ep.id && it.seasonNumber == ep.seasonNumber && it.episodeNumber == ep.episodeNumber }
                        if (matches.size == 1) matches.single().sourceAvailability?.let { put(episodeSourceKey(ep), it) }
                    }
                }
                emit(values)
                val pending = values.size < episodes.size || values.values.any { it.reason in setOf("recheck_queued", "not_checked", "expired", "evidence_not_recorded", "prepared_source_requires_recheck") }
                delay(if (pending && pendingPolls++ < 15) 1_000 else 15_000)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            if (error is ApiError && error.statusCode in setOf(404, 405, 501)) synchronized(lock) { if (owner == batch) dirty = false }
            emit(emptyMap())
        } finally {
            clear(batch)
        }
    }
}

/** A listed local copy still needs language/access probing; remote absence is not total absence. */
fun episodeSourcePresentation(hint: SourceHint?, inLibrary: Boolean): SourceHint? {
    if (hint == null) return null
    return if (inLibrary && hint.dimArtwork) hint.copy(kind = SourceHint.Kind.UNKNOWN, label = "Local version needs checking") else hint
}
