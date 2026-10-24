package com.strmr.tv.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The parts of a Media3 `Format` the Info panel's stream details read, so the formatting stays
 * a pure function (see [StreamInfoFormat.of]). Unknown numbers are null.
 */
data class TrackFacts(
    val mime: String? = null,
    val codecs: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    /** Bits per second. */
    val bitrate: Int? = null,
    val colorTransfer: Int? = null,
    val channels: Int? = null,
    val language: String? = null,
    val label: String? = null,
)

/** What the server said about the source the player opened: Info's Source row is built from it. */
data class SourceHints(
    val serviceType: String? = null,
    val debridProvider: String? = null,
    val indexer: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    /** The server's Dolby Vision codec string for the file ("dvhe.07.06"), before any on-device rewrite. */
    val dolbyVisionProfile: String? = null,
)

/** The Info panel's stream-details rows (File / Video / Dynamic range / Audio / Source). */
object StreamInfo {
    // ---- Video ----

    /** "3840×2160 (4K) · HEVC Main 10 · 23.976 fps · 18.4 Mbps". */
    fun video(f: TrackFacts): String? = listOfNotNull(
        resolution(f.width, f.height), videoCodec(f.mime, f.codecs), frameRate(f.frameRate), bitrate(f.bitrate),
    ).joinToString(" · ").ifEmpty { null }

    fun resolution(width: Int?, height: Int?): String? {
        val w = width?.takeIf { it > 0 } ?: return null
        val h = height?.takeIf { it > 0 } ?: return null
        return "$w×$h (${bucket(w, h)})"
    }

    /** By height as Expo buckets it; the width as well so scope frames (3840×1608) keep their class. */
    fun bucket(width: Int, height: Int): String = when {
        height > 1080 || width >= 3200 -> "4K"
        height > 720 || width >= 1800 -> "1080p"
        height == 720 || width >= 1200 -> "720p"
        height >= 480 -> "480p"
        else -> "SD"
    }

    fun videoCodec(mime: String?, codecs: String?): String? {
        val c = codecs?.lowercase()?.split(',')?.map { it.trim() }.orEmpty()
        val base = when {
            mime == MimeTypes.VIDEO_H265 || c.any { it.startsWith("hvc1") || it.startsWith("hev1") } -> "HEVC"
            mime == MimeTypes.VIDEO_H264 || c.any { it.startsWith("avc") } -> "H.264"
            mime == MimeTypes.VIDEO_AV1 || c.any { it.startsWith("av01") } -> "AV1"
            mime == MimeTypes.VIDEO_VP9 || c.any { it.startsWith("vp09") } -> "VP9"
            mime == MimeTypes.VIDEO_VP8 -> "VP8"
            mime == MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
            mime == MimeTypes.VIDEO_DOLBY_VISION || c.any { it.startsWith("dvh") } ->
                if (c.any { it.startsWith("dva") }) "H.264" else if (c.any { it.startsWith("dav1") }) "AV1" else "HEVC"
            mime.isNullOrEmpty() -> return null
            else -> mime.substringAfter('/').uppercase()
        }
        val profile = when (base) {
            "HEVC" -> c.firstOrNull { it.startsWith("hvc1") || it.startsWith("hev1") }?.let(::hevcProfile)
            "H.264" -> c.firstOrNull { it.startsWith("avc") }?.let(::avcProfile)
            else -> null
        }
        return listOfNotNull(base, profile).joinToString(" ")
    }

    /** `hvc1.2.4.L153.B0`: general_profile_idc, optionally after a profile-space letter. */
    private fun hevcProfile(codec: String): String? = when (codec.split('.').getOrNull(1)?.trimStart('a', 'b', 'c')?.toIntOrNull()) {
        1 -> "Main"
        2 -> "Main 10"
        3 -> "Main Still"
        4 -> "RExt"
        else -> null
    }

    /** `avc1.640028`: profile_idc in the first hex byte. */
    private fun avcProfile(codec: String): String? = when (codec.split('.').getOrNull(1)?.take(2)?.toIntOrNull(16)) {
        66 -> "Baseline"
        77 -> "Main"
        100 -> "High"
        110 -> "High 10"
        else -> null
    }

    /** "23.976 fps", "25 fps". */
    fun frameRate(fps: Float?): String? {
        val v = fps?.takeIf { it > 0 } ?: return null
        val rounded = v.roundToInt()
        val text = if (abs(v - rounded) < 0.005f) rounded.toString()
        else String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
        return "$text fps"
    }

    /** "18.4 Mbps", "768 kbps". */
    fun bitrate(bps: Int?): String? {
        val v = bps?.takeIf { it > 0 } ?: return null
        return if (v >= 1_000_000) String.format(Locale.US, "%.1f Mbps", v / 1e6) else "${(v / 1000.0).roundToInt()} kbps"
    }

    // ---- Dynamic range ----

    /**
     * SDR / HDR10 / HDR10+ / HLG / Dolby Vision (Profile N). `sourceDv` is the server's codec string
     * for the file: a Profile 7 file this device rewrote (player/dv7) still reports Profile 7, with
     * what it plays as. HDR10+ isn't signalled in the Format, so it comes from the release name.
     */
    fun dynamicRange(f: TrackFacts, sourceDv: String? = null, releaseName: String? = null): String? {
        if (f.mime == null && f.codecs == null && f.colorTransfer == null) return null
        val playingProfile = dolbyVisionProfile(f.codecs)
        val isDv = f.mime == MimeTypes.VIDEO_DOLBY_VISION || f.codecs?.lowercase()?.let { "dvh" in it || "dva" in it || "dav1" in it } == true
        val sourceProfile = dolbyVisionProfile(sourceDv)
        val pq = f.colorTransfer == C.COLOR_TRANSFER_ST2084
        if (sourceProfile == 7 && (isDv || pq) && playingProfile != 7) {
            return if (isDv) "Dolby Vision Profile 7 (as 8.1)" else "Dolby Vision Profile 7 (HDR10 base layer)"
        }
        if (isDv) return listOfNotNull("Dolby Vision", (playingProfile ?: sourceProfile)?.let { "Profile $it" }).joinToString(" ")
        return when (f.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> if (releaseName?.let(::isHdr10Plus) == true) "HDR10+" else "HDR10"
            C.COLOR_TRANSFER_HLG -> "HLG"
            else -> "SDR"
        }
    }

    /** `dvhe.08.06` → 8. */
    fun dolbyVisionProfile(codecs: String?): Int? =
        codecs?.let { Regex("(?i)(?:dvhe|dvh1|dvav|dva1|dav1)\\.0?(\\d{1,2})").find(it) }?.groupValues?.get(1)?.toIntOrNull()

    private fun isHdr10Plus(name: String) = Regex("(?i)hdr10(\\+|plus|p\\b)").containsMatchIn(name)

    // ---- Audio ----

    /** "E-AC-3 · 5.1 · Atmos · English · 768 kbps". */
    fun audio(f: TrackFacts): String? {
        val codec = audioCodec(f.mime, f.codecs)
        val label = f.label?.lowercase().orEmpty()
        val atmos = f.mime == MimeTypes.AUDIO_E_AC3_JOC || "atmos" in label
        val language = f.language?.takeIf { it.isNotBlank() && it.lowercase() !in setOf("und", "unk", "mis", "zxx") }
            ?.let(SubtitleLanguages::displayName)
        return listOfNotNull(codec, channels(f.channels), "Atmos".takeIf { atmos }, language, bitrate(f.bitrate))
            .joinToString(" · ").ifEmpty { null }
    }

    fun audioCodec(mime: String?, codecs: String? = null): String? = when (mime) {
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
        MimeTypes.AUDIO_AC3 -> "AC-3"
        MimeTypes.AUDIO_AC4 -> "AC-4"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
        MimeTypes.AUDIO_DTS_X -> "DTS:X"
        MimeTypes.AUDIO_AAC, "audio/aac" -> "AAC"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_MPEG_L2 -> "MP2"
        MimeTypes.AUDIO_RAW -> "PCM"
        MimeTypes.AUDIO_ALAC -> "ALAC"
        null, "" -> codecs?.takeIf { it.isNotBlank() }?.uppercase()
        else -> mime.substringAfter('/').removePrefix("x-").removePrefix("vnd.").uppercase()
    }

    fun channels(count: Int?): String? = when (val n = count?.takeIf { it > 0 } ?: return null) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$n ch"
    }

    // ---- Source ----

    /**
     * "Debrid · Real-Debrid", "Usenet · NZBgeek", "Plex · Films", "Local", from the stream path
     * and the server's hints (as Expo's player infers its source service type). An explicit
     * service type wins over the path; null without a path.
     */
    fun source(path: String?, hints: SourceHints?, libraryOrigin: String? = null): String? {
        libraryOrigin?.takeIf { it.isNotBlank() }?.let { return it }
        val p = path?.trim()?.lowercase()?.ifEmpty { null } ?: return null
        val explicit = hints?.serviceType?.trim()?.lowercase()?.takeIf { it in serviceTypes }
        val type = explicit ?: when {
            p.startsWith("localmedia:") -> "local"
            p.startsWith("plexmedia:") -> "plex"
            p.startsWith("jellyfinmedia:") -> "jellyfin"
            "/debrid/" in p || p.startsWith("debrid/") -> "debrid"
            else -> "usenet"
        }
        return when (type) {
            "debrid" -> {
                val raw = hints?.debridProvider?.ifBlank { null }
                    ?: hints?.attributes?.get("debridProvider")?.ifBlank { null }
                    ?: hints?.attributes?.get("provider")?.ifBlank { null }
                    ?: debridProviderFromPath(path)
                listOfNotNull("Debrid", debridProviderName(raw)).joinToString(" · ")
            }
            "usenet" -> listOfNotNull("Usenet", hints?.indexer?.trim()?.ifEmpty { null }).joinToString(" · ")
            "local" -> "Local"
            "plex" -> "Plex"
            "jellyfin" -> "Jellyfin"
            "peartube" -> "PearTube"
            else -> null
        }
    }

    private val serviceTypes = setOf("debrid", "usenet", "local", "plex", "jellyfin", "peartube")

    private val providerNames = mapOf(
        "alldebrid" to "AllDebrid", "debridlink" to "Debrid-Link", "premiumize" to "Premiumize",
        "realdebrid" to "Real-Debrid", "torbox" to "TorBox",
    )

    /** Pretty debrid provider name (frontend/utils/debrid-provider.ts); unknown names pass through trimmed. */
    fun debridProviderName(provider: String?): String? {
        val normalized = provider?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.ifEmpty { null } ?: return null
        return providerNames[normalized] ?: provider.trim()
    }

    /** `/debrid/realdebrid/…` → `realdebrid`. */
    fun debridProviderFromPath(path: String?): String? {
        val segment = path?.let { Regex("(?i)(?:^|/)debrid/([^/?#]+)").find(it) }?.groupValues?.get(1) ?: return null
        return runCatching { java.net.URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
    }

    fun live(channelName: String?): String = listOfNotNull("Live TV", channelName?.trim()?.ifEmpty { null }).joinToString(" · ")

    fun recording(channelName: String?): String = listOfNotNull("Recording", channelName?.trim()?.ifEmpty { null }).joinToString(" · ")
}
