package com.strmr.tv.ui.sports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.liveStremioStreams
import com.strmr.tv.sports.SportsAutoPlay
import com.strmr.tv.sports.SportsStreamLookup
import com.strmr.tv.sports.SportsStreamMatch
import com.strmr.tv.sports.SportsStreamOptions
import com.strmr.tv.sports.sportsStreams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/**
 * Watch Live / Find Streams' search, shared by both apps' pickers: the live channels that look
 * like they carry the event (`/api/sports/game/{id}/streams`), strongest match first, each
 * Stremio addon channel's streams looked up a few at a time ([SportsStreamOptions]), and a
 * single strong match of a live event played at once. [broad] ("Search All Sources") widens the
 * search past the admin's default sources. Main thread only.
 */
class SportsStreamSearch(private val target: SportsWatchTarget) {
    var matches by mutableStateOf<List<SportsStreamMatch>>(emptyList())
        private set
    var lookups by mutableStateOf<Map<String, SportsStreamLookup>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set
    var broad by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
        private set

    val rows: List<SportsStreamMatch> get() = SportsStreamOptions.rows(matches, lookups)

    /** One search at the current [broad]; cancelling the caller cancels the lookups still running. */
    suspend fun run(pick: (SportsStreamMatch) -> Unit) {
        loading = true
        val groups = try {
            ApiClient.sportsStreams(target.id, broad, target.kind, target.parentId)
                .filter { (it.confidenceTier == "strong" || it.confidenceTier == "possible") && it.primary.channelUrl.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't search channels"
            loading = false
            return
        }
        val feeds = groups.flatMap { listOf(it.primary) + it.alternatives.orEmpty() }
            .filter { it.channelUrl.isNotEmpty() }.distinctBy { it.key }
        // Strong matches first, otherwise in the server's order.
        val found = feeds.filter { it.isStrong } + feeds.filterNot { it.isStrong }
        matches = found
        error = if (found.isEmpty()) "No channel looks like it carries this ${if (target.kind == null) "game" else "session"}." else null
        // One clear answer for a game in progress: no need to ask (once its streams are known).
        val candidate = SportsStreamOptions.autoPlayCandidate(groups, broad, target.live)
        val urls = SportsStreamOptions.lookupUrls(found).filter { lookups[it] !is SportsStreamLookup.Found }
            .sortedBy { it != candidate?.channelUrl }
        lookups = lookups + urls.associateWith { SportsStreamLookup.Pending }
        when (val decision = SportsStreamOptions.autoPlay(candidate, lookups)) {
            is SportsAutoPlay.Play -> return pick(decision.match)
            SportsAutoPlay.Wait -> Unit
            SportsAutoPlay.Show -> loading = false
        }
        val permits = Semaphore(SportsStreamOptions.CONCURRENT_LOOKUPS)
        coroutineScope {
            urls.forEach { url ->
                launch {
                    val channel = found.first { it.channelUrl == url }.channel
                    val result = permits.withPermit { lookUp(channel) }
                    lookups = lookups + (url to result)
                    if (loading && url == candidate?.channelUrl) {
                        (SportsStreamOptions.autoPlay(candidate, lookups) as? SportsAutoPlay.Play)?.let { pick(it.match) }
                        loading = false
                    }
                }
            }
        }
    }

    companion object {
        /** "Source · Programme · why it matched · 92%", with the channel after the source on a stream row (its headline is the stream). */
        fun details(match: SportsStreamMatch): String {
            val percent = match.confidence?.let { "${Math.round(it * 100)}%" }
            val channel = match.channelName.takeIf { match.streamLabel != null }
            return listOfNotNull(match.sourceName, channel, match.programTitle, match.matchReason, percent).filter { it.isNotEmpty() }.joinToString(" · ")
        }
    }
}

/** The channel's streams, or [SportsStreamLookup.Failed] (the plain row stays and the server picks). */
private suspend fun lookUp(channel: LiveChannel): SportsStreamLookup = try {
    withTimeout(SportsStreamOptions.LOOKUP_TIMEOUT_MS) {
        SportsStreamLookup.Found(SportsStreamOptions.clean(ApiClient.liveStremioStreams(channel)))
    }
} catch (e: TimeoutCancellationException) {
    SportsStreamLookup.Failed
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    SportsStreamLookup.Failed
}
