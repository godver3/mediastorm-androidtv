package com.strmr.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class WebVttTest {
    private val sample = """
        WEBVTT

        NOTE a comment --> not a cue? no: NOTE blocks have no timing line of their own

        1
        00:00:01.000 --> 00:00:02.500 align:center line:90%
        <i>Hello</i> &amp; welcome

        00:00:03.000 --> 00:00:04.000
        Two
        lines

        00:00:05.000 --> 00:00:05.000
        zero length

        00:00:06.000 --> 00:00:07.000

    """.trimIndent()

    @Test fun countsFullAndShortTimings() {
        assertEquals(0, WebVtt.countCues(""))
        assertEquals(2, WebVtt.countCues("00:00:01.000 --> 00:00:02.000\na\n\n00:00:03.000-->00:00:04.000\nb"))
        assertEquals(1, WebVtt.countCues("WEBVTT\n\n01:02.000 --> 01:03.500\nshort"))
        // Full timings win: short ones aren't counted on top.
        assertEquals(1, WebVtt.countCues("00:00:01.000 --> 00:00:02.000\n\n01:02.000 --> 01:03.500"))
    }

    @Test fun parsesCuesDroppingMarkupSettingsAndEmpties() {
        val cues = WebVtt.parse(sample)
        assertEquals(
            listOf(
                VttCue(1_000, 2_500, "Hello & welcome"),
                VttCue(3_000, 4_000, "Two\nlines"),
            ),
            cues,
        )
    }

    @Test fun parsesShortTimingsCrLfAndBom() {
        val cues = WebVtt.parse("﻿WEBVTT\r\n\r\n01:02.5 --> 1:01:03,250\r\n<c.yellow>Hi</c>\r\n")
        assertEquals(listOf(VttCue(62_500, 3_663_250, "Hi")), cues)
    }

    @Test fun activeCuesAtAPosition() {
        val cues = listOf(VttCue(0, 1_000, "a"), VttCue(500, 2_000, "b"), VttCue(2_000, 3_000, "c"))
        assertEquals(listOf("a", "b"), WebVtt.active(cues, 700).map { it.text })
        assertEquals(listOf("c"), WebVtt.active(cues, 2_000).map { it.text })
        assertEquals(emptyList<VttCue>(), WebVtt.active(cues, 3_000))
    }
}
