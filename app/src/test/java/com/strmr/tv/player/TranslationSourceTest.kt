package com.strmr.tv.player

import com.strmr.tv.api.SubtitleStreamInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationSourceTest {
    @Test fun metadataLanguageIsMappedOrPassedThrough() {
        assertEquals("fr", TranslationSource.language("fre", null))
        assertEquals("pl", TranslationSource.language("POL", "English"))
        assertEquals("de", TranslationSource.language("de", null))
        assertEquals("ja", TranslationSource.language("Japanese", null))
        // Unknown codes go to the server as they are; it normalises them.
        assertEquals("hun", TranslationSource.language("hun", null))
    }

    @Test fun labelLanguageWhenNoMetadata() {
        assertEquals("pl", TranslationSource.language(null, "Polski · POL"))
        assertEquals("ru", TranslationSource.language("", "English, Russian"))
        // A non-English name wins over an earlier English one; then 3-letter codes.
        assertEquals("es", TranslationSource.language("und", "English · Español"))
        assertEquals("en", TranslationSource.language(null, "Full · ENG"))
        assertEquals("en", TranslationSource.language(null, "Track 3"))
        assertEquals("en", TranslationSource.language(null, null))
    }

    @Test fun extractIndexIsTheStreamPosition() {
        val streams = listOf(
            SubtitleStreamInfo(index = 5, absoluteIndex = 5, language = "eng"),
            SubtitleStreamInfo(index = 6, absoluteIndex = 6, language = "fre"),
        )
        // Matroska track 7 is ffprobe stream 6: the second subtitle stream.
        assertEquals(1, TranslationSource.extractIndex(7, streams, 0))
        assertEquals("fre", TranslationSource.stream(7, streams)?.language)
        // Unknown to the server, or no server list: the player's own position.
        assertEquals(3, TranslationSource.extractIndex(42, streams, 3))
        assertEquals(2, TranslationSource.extractIndex(7, emptyList(), 2))
        assertEquals(1, TranslationSource.extractIndex(null, streams, 1))
    }
}
