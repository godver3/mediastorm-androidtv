package com.strmr.tv.sports

import com.strmr.tv.live.LiveStremioStream

/** Where the picker is with a Stremio channel's stream list (`/api/live/stremio/streams`). */
sealed interface SportsStreamLookup {
    data object Pending : SportsStreamLookup
    data object Failed : SportsStreamLookup
    data class Found(val streams: List<LiveStremioStream>) : SportsStreamLookup
}

/** Whether the picker plays a match without asking. */
sealed interface SportsAutoPlay {
    /** The match's Stremio streams are still being looked up. */
    data object Wait : SportsAutoPlay
    data object Show : SportsAutoPlay
    data class Play(val match: SportsStreamMatch) : SportsAutoPlay
}

/**
 * The stream picker's rows (Expo `renderOptions` in `sports-stream-options.ts`): a channel that
 * is a Stremio addon's stream list becomes one row per stream, in the addon's order, titled
 * "<channel> · <stream>"; while its lookup runs the plain row shows, and when the lookup fails or
 * finds nothing the plain row stays (the server picks).
 */
object SportsStreamOptions {
    /** At most this many stream lookups run at once. */
    const val CONCURRENT_LOOKUPS = 4

    /** One lookup's allowance (Expo `perSourceMs`); past it the channel keeps its plain row. */
    const val LOOKUP_TIMEOUT_MS = 25_000L

    /** The stream's label, else its title or name, else "Feed N". */
    fun label(stream: LiveStremioStream): String =
        listOf(stream.label, stream.title, stream.name).firstOrNull { !it.isNullOrBlank() }?.trim() ?: "Feed ${stream.index + 1}"

    /** The addon's streams with a usable index, each index once, in its order. */
    fun clean(streams: List<LiveStremioStream>): List<LiveStremioStream> = streams.filter { it.index >= 0 }.distinctBy { it.index }

    /** The channel URLs whose streams are worth looking up, once each, in row order. */
    fun lookupUrls(matches: List<SportsStreamMatch>): List<String> =
        matches.filter { it.channel.hasStremioSources }.map { it.channelUrl }.distinct()

    /**
     * The rows for [matches] given the [lookups] so far, without repeats of the same source, URL
     * and stream. The first stream of a channel keeps the channel row's key, so focus on the row
     * stays put when it expands.
     */
    fun rows(matches: List<SportsStreamMatch>, lookups: Map<String, SportsStreamLookup>): List<SportsStreamMatch> =
        matches.flatMap { match ->
            if (!match.channel.hasStremioSources) return@flatMap listOf(match)
            when (val lookup = lookups[match.channelUrl]) {
                is SportsStreamLookup.Found -> lookup.streams.ifEmpty { null }?.mapIndexed { i, stream ->
                    match.copy(stremioStreamIndex = stream.index, streamLabel = label(stream),
                               rowKey = if (i == 0) match.key else "${match.key}#${stream.index}")
                } ?: listOf(match)
                SportsStreamLookup.Failed -> listOf(match)
                SportsStreamLookup.Pending, null -> listOf(match.copy(loadingStreams = true))
            }
        }.distinctBy { Triple(it.sourceId.orEmpty(), it.channelUrl, it.stremioStreamIndex) }.distinctBy { it.key }

    /**
     * The match to play without asking: a live event's single strong match with no
     * alternatives, on the first search only.
     */
    fun autoPlayCandidate(groups: List<SportsStreamGroup>, broad: Boolean, live: Boolean): SportsStreamMatch? {
        val only = groups.singleOrNull() ?: return null
        return only.primary.takeIf { !broad && live && only.confidenceTier == "strong" && only.alternatives.isNullOrEmpty() }
    }

    /**
     * Whether to play [candidate] now: at once when it isn't a Stremio channel; for one that is,
     * once its lookup is done, on its only stream, or as the plain channel when the lookup failed
     * or found nothing. With several streams the user picks.
     */
    fun autoPlay(candidate: SportsStreamMatch?, lookups: Map<String, SportsStreamLookup>): SportsAutoPlay {
        if (candidate == null) return SportsAutoPlay.Show
        if (!candidate.channel.hasStremioSources) return SportsAutoPlay.Play(candidate)
        return when (val lookup = lookups[candidate.channelUrl]) {
            is SportsStreamLookup.Found -> when (lookup.streams.size) {
                0 -> SportsAutoPlay.Play(candidate)
                1 -> lookup.streams.single().let {
                    SportsAutoPlay.Play(candidate.copy(stremioStreamIndex = it.index, streamLabel = label(it)))
                }
                else -> SportsAutoPlay.Show
            }
            SportsStreamLookup.Failed -> SportsAutoPlay.Play(candidate)
            SportsStreamLookup.Pending, null -> SportsAutoPlay.Wait
        }
    }
}
