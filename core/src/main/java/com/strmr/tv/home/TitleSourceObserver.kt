package com.strmr.tv.home

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.TitleSourceCoordinate
import com.strmr.tv.api.TitleSourceDemand
import com.strmr.tv.api.TitleSourceResponse
import com.strmr.tv.api.toTitleSourceBody
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

fun titleSourceKey(title: TitleSourceCoordinate): String = "${title.mediaType}|${title.titleId}"

/** Episode, library and action cards never inherit a general series check. */
fun HomeCard.titleSourceCoordinate(): TitleSourceCoordinate? {
    val value = target ?: return null
    if (episode != null || value.fromLibrary || value.titleId.isBlank()) return null
    return TitleSourceCoordinate(value.titleId, if (value.isMovie) "movie" else "series")
}

interface TitleSourceGateway {
    fun isCurrent(): Boolean
    suspend fun update(view: String, body: TitleSourceDemand): TitleSourceResponse
}
class ServerTitleSourceGateway : TitleSourceGateway {
    private val profile = ApiClient.profileId
    private val server = ApiClient.baseUrl
    private val account = ApiClient.session?.accountId
    private val remote = ApiClient.remoteCredential
    private val path = ApiClient.profilePath
    override fun isCurrent() = profile == ApiClient.profileId && server == ApiClient.baseUrl && account == ApiClient.session?.accountId && remote == ApiClient.remoteCredential
    override suspend fun update(view: String, body: TitleSourceDemand): TitleSourceResponse {
        if (!isCurrent()) throw CancellationException("Title source context changed")
        return ApiClient.send("PUT", "$path/source-availability/titles/$view", body = body.toTitleSourceBody(), promptOnLock = false, timeoutMs = 5_000)
    }
}

/** One stable view ID and ordered versions survive scroll updates, including late requests. */
class TitleSourceObserver(
    private val gateway: TitleSourceGateway,
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
        val cleared = withContext(NonCancellable) {
            withTimeoutOrNull(2_000) { runCatching { gateway.update(viewId, TitleSourceDemand(clearVersion)) }.isSuccess } == true
        }
        if (cleared) synchronized(lock) { if (owner == batch && version == clearVersion) dirty = false }
    }
    fun states(visible: List<TitleSourceCoordinate>): Flow<Map<String, SourceAvailability>> = flow {
        val batch = claim()
        emit(emptyMap())
        val titles = visible.filter { it.titleId.isNotBlank() && it.mediaType in setOf("movie", "series") }
            .distinct().take(24)
        if (titles.isEmpty() || !gateway.isCurrent()) { clear(batch); return@flow }
        var pendingPolls = 0
        try {
            while (currentCoroutineContext().isActive && gateway.isCurrent() && owns(batch)) {
                val requestVersion = next(batch) ?: return@flow
                val body = TitleSourceDemand(requestVersion, titles)
                val reply = gateway.update(viewId, body)
                if (!gateway.isCurrent() || !owns(batch)) return@flow
                if (!reply.enabled) { synchronized(lock) { if (owner == batch) dirty = false }; return@flow }
                if (reply.version != requestVersion) return@flow
                val values = buildMap {
                    for (title in titles) {
                        val matches = reply.items.filter { it.titleId == title.titleId && it.mediaType == title.mediaType }
                        if (matches.size == 1) matches.single().sourceAvailability?.let { put(titleSourceKey(title), it) }
                    }
                }
                emit(values)
                val pending = values.size < titles.size || values.values.any { it.reason in setOf("recheck_queued", "not_checked", "expired", "evidence_not_recorded", "prepared_source_requires_recheck") }
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
