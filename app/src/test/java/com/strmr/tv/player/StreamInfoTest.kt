package com.strmr.tv.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamInfoTest {
    @Test fun videoLine() {
        val uhd = TrackFacts(MimeTypes.VIDEO_H265, "hvc1.2.4.L153.B0", 3840, 2160, 23.976f, 18_400_000)
        assertEquals("3840×2160 (4K) · HEVC Main 10 · 23.976 fps · 18.4 Mbps", StreamInfo.video(uhd))
        val hd = TrackFacts(MimeTypes.VIDEO_H264, "avc1.640028", 1920, 1080, 25f)
        assertEquals("1920×1080 (1080p) · H.264 High · 25 fps", StreamInfo.video(hd))
        assertEquals("HEVC", StreamInfo.videoCodec(MimeTypes.VIDEO_DOLBY_VISION, "dvhe.08.06"))
        assertEquals("29.97 fps", StreamInfo.frameRate(29.97f))
        assertNull(StreamInfo.video(TrackFacts()))
    }

    @Test fun resolutionBuckets() {
        assertEquals("4K", StreamInfo.bucket(3840, 1608))
        assertEquals("1080p", StreamInfo.bucket(1920, 800))
        assertEquals("720p", StreamInfo.bucket(1280, 720))
        assertEquals("480p", StreamInfo.bucket(720, 480))
        assertEquals("SD", StreamInfo.bucket(640, 360))
    }

    @Test fun dynamicRange() {
        val pq = TrackFacts(MimeTypes.VIDEO_H265, "hvc1.2.4.L153", colorTransfer = C.COLOR_TRANSFER_ST2084)
        assertEquals("HDR10", StreamInfo.dynamicRange(pq))
        assertEquals("HDR10+", StreamInfo.dynamicRange(pq, releaseName = "Movie.2160p.HDR10+.HEVC-GRP"))
        assertEquals("HLG", StreamInfo.dynamicRange(pq.copy(colorTransfer = C.COLOR_TRANSFER_HLG)))
        assertEquals("SDR", StreamInfo.dynamicRange(TrackFacts(MimeTypes.VIDEO_H264, colorTransfer = C.COLOR_TRANSFER_SDR)))
        assertEquals("SDR", StreamInfo.dynamicRange(TrackFacts(MimeTypes.VIDEO_H264)))
        val dv8 = TrackFacts(MimeTypes.VIDEO_DOLBY_VISION, "dvhe.08.06", colorTransfer = C.COLOR_TRANSFER_ST2084)
        assertEquals("Dolby Vision Profile 8", StreamInfo.dynamicRange(dv8))
        assertEquals("Dolby Vision Profile 5", StreamInfo.dynamicRange(TrackFacts(MimeTypes.VIDEO_DOLBY_VISION, "dvh1.05.06")))
        // A Profile 7 file rewritten on this device (player/dv7).
        assertEquals("Dolby Vision Profile 7 (as 8.1)", StreamInfo.dynamicRange(dv8, sourceDv = "dvhe.07.06"))
        assertEquals("Dolby Vision Profile 7 (HDR10 base layer)", StreamInfo.dynamicRange(pq, sourceDv = "dvhe.07.06"))
        assertEquals("Dolby Vision Profile 7", StreamInfo.dynamicRange(TrackFacts(MimeTypes.VIDEO_DOLBY_VISION, "dvhe.07.06"), "dvhe.07.06"))
        assertNull(StreamInfo.dynamicRange(TrackFacts()))
    }

    @Test fun audioLine() {
        val atmos = TrackFacts(MimeTypes.AUDIO_E_AC3_JOC, channels = 6, language = "eng", bitrate = 768_000)
        assertEquals("E-AC-3 · 5.1 · Atmos · English · 768 kbps", StreamInfo.audio(atmos))
        val truehd = TrackFacts(MimeTypes.AUDIO_TRUEHD, channels = 8, language = "und", label = "TrueHD Atmos 7.1")
        assertEquals("TrueHD · 7.1 · Atmos", StreamInfo.audio(truehd))
        assertEquals("AAC · Stereo", StreamInfo.audio(TrackFacts(MimeTypes.AUDIO_AAC, channels = 2)))
        assertEquals("DTS-HD · 3 ch", StreamInfo.audio(TrackFacts(MimeTypes.AUDIO_DTS_HD, channels = 3)))
        assertEquals("AC-3", StreamInfo.audioCodec(MimeTypes.AUDIO_AC3))
        assertEquals("DTS", StreamInfo.audioCodec(MimeTypes.AUDIO_DTS))
        assertEquals("FLAC", StreamInfo.audioCodec(MimeTypes.AUDIO_FLAC))
        assertEquals("Opus", StreamInfo.audioCodec(MimeTypes.AUDIO_OPUS))
        assertNull(StreamInfo.audio(TrackFacts()))
    }

    @Test fun sourceFromPathAndHints() {
        assertEquals("Debrid · Real-Debrid", StreamInfo.source("/debrid/realdebrid/abc/file.mkv", null))
        assertEquals("Debrid · TorBox", StreamInfo.source("/debrid/x/1", SourceHints(debridProvider = "Tor-Box")))
        assertEquals("Debrid · AllDebrid", StreamInfo.source("/stream/1", SourceHints("debrid", attributes = mapOf("provider" to "alldebrid"))))
        assertEquals("Debrid · SomeNew", StreamInfo.source("/stream/1", SourceHints("debrid", debridProvider = " SomeNew ")))
        assertEquals("Debrid", StreamInfo.source("/stream/1", SourceHints("debrid")))
        assertEquals("Usenet · NZBgeek", StreamInfo.source("/usenet/1/file.mkv", SourceHints(indexer = "NZBgeek")))
        assertEquals("Usenet", StreamInfo.source("/stream/1", null))
        // An explicit service type wins over the path.
        assertEquals("Usenet · Geek", StreamInfo.source("/debrid/realdebrid/1", SourceHints("usenet", indexer = "Geek")))
        assertEquals("Local", StreamInfo.source("localmedia:abc", null))
        assertEquals("Plex", StreamInfo.source("plexmedia:1", null))
        assertEquals("Jellyfin", StreamInfo.source("jellyfinmedia:1", null))
        assertEquals("Plex · Films", StreamInfo.source("plexmedia:1", null, libraryOrigin = "Plex · Films"))
        assertNull(StreamInfo.source(null, null))
        assertEquals("Live TV · BBC One", StreamInfo.live("BBC One"))
        assertEquals("Recording", StreamInfo.recording(""))
    }

    @Test fun debridProviders() {
        assertEquals("Debrid-Link", StreamInfo.debridProviderName("debrid_link"))
        assertEquals("Premiumize", StreamInfo.debridProviderName("PREMIUMIZE"))
        assertNull(StreamInfo.debridProviderName("  "))
        assertEquals("real debrid", StreamInfo.debridProviderFromPath("/api/debrid/real%20debrid/x"))
    }
}
