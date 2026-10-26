package com.strmr.tv.player

import com.strmr.tv.api.PlaybackSettings
import com.strmr.tv.api.ProbedAudioTrack
import com.strmr.tv.api.ProbedSubtitleTrack
import com.strmr.tv.api.SubtitleStreamInfo
import com.strmr.tv.api.TrackSelection
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveStream

/** Tracks picked by hand for a manual source, with the probed lists their numbers refer to. */
data class ManualSourceTracks(
    val choice: ManualTrackChoice,
    val audio: List<ProbedAudioTrack>,
    val subtitles: List<ProbedSubtitleTrack>,
)

/** What the player opens: the stream and the server's track choice for it. */
data class PlayerSession(
    val request: PlaybackRequest,
    val url: String,
    val tracks: TrackSelection?,
    /** Trailers and live channels play without progress reports. */
    val reportsProgress: Boolean = true,
    /** A live channel's server HLS session, kept alive while it plays and stopped after. */
    val liveSessionId: String? = null,
    /** A live channel: no scrubber or seeking, closed captions offered. */
    val isLive: Boolean = false,
    /** The backend stream path (seek-preview thumbnails are keyed by it); null for trailers and live. */
    val streamPath: String? = null,
    /** The release playing (prequeue display name or the picked source's title), for subtitle matching. */
    val releaseName: String? = null,
    /** The file's subtitle streams as the server probed them; empty when it sent none. */
    val subtitleStreams: List<SubtitleStreamInfo> = emptyList(),
    /** The profile's playback settings (track languages, subtitle mode), read as the stream resolved. */
    val playback: PlaybackSettings? = null,
    /** [tracks] holds tracks picked by hand in manual selection: this device's track overrides don't apply. */
    val manualTracks: Boolean = false,
    /** Switch Source: the languages that were playing, applied over this stream's choice. */
    val carried: CarriedTracks? = null,
    /** A library file: no server track choice, so the profile's languages pick the tracks here. */
    val pickTracksOnDevice: Boolean = false,
    /** A recording still recording, over a growing HLS session: opens another one if its playlist ends early. */
    val growingRestart: (suspend () -> LiveStream)? = null,
    /** A live channel: tunes it again when its source drops ([LiveRetune]); `true` asks for HLS. */
    val retuneLive: (suspend (forceHls: Boolean) -> LiveStream)? = null,
    /** The live channel playing, which Add Screen hands to the multiscreen. */
    val liveChannel: LiveChannel? = null,
    /** Where the stream comes from, for Info ("Debrid · Real-Debrid", "Live TV · BBC One"); null for trailers. */
    val sourceLabel: String? = null,
    /** The server's Dolby Vision codec string for the file ("dvhe.07.06"), before any on-device Profile 7 rewrite. */
    val sourceDolbyVision: String? = null,
    /** The server's probe saw HDR10 (PQ) video in the file. */
    val sourceHdr10: Boolean = false,
)

/** A resolved stream: its path, the server's track choice and what online subtitles match against. */
data class ResolvedStream(
    val path: String,
    val tracks: TrackSelection?,
    val releaseName: String? = null,
    val subtitleStreams: List<SubtitleStreamInfo> = emptyList(),
    val manualTracks: Boolean = false,
    val pickTracksOnDevice: Boolean = false,
    /** The server's service type / provider / indexer for the source, for Info's Source row. */
    val source: SourceHints? = null,
)
