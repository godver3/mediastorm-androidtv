package com.strmr.tv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSkipPolicyTest {
    @Test fun `one profile's intro choice never enables recap or credits`() {
        assertTrue(ProfileSkipPolicy.enabled("intro", true, false, false, false))
        assertFalse(ProfileSkipPolicy.enabled("recap", true, false, false, false))
        assertFalse(ProfileSkipPolicy.enabled("outro", true, false, false, false))
    }

    @Test fun `credits countdown is retained when another episode exists`() {
        assertTrue(ProfileSkipPolicy.enabled("outro", false, false, true, false))
        assertFalse(ProfileSkipPolicy.enabled("outro", false, false, true, true))
    }

    @Test fun `unknown segments and an unconfigured profile never skip`() {
        assertFalse(ProfileSkipPolicy.enabled("preview", true, true, true, false))
        for (type in listOf("intro", "recap", "outro")) {
            assertFalse(ProfileSkipPolicy.enabled(type, false, false, false, false))
        }
    }
}
