package com.strmr.tv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSkipPolicyTest {
    @Test fun `intro and recap follow their own profile choice`() {
        assertTrue(ProfileSkipPolicy.enabled("intro", intro = true, recap = false))
        assertFalse(ProfileSkipPolicy.enabled("recap", intro = true, recap = false))
        assertTrue(ProfileSkipPolicy.enabled("recap", intro = false, recap = true))
    }

    @Test fun `credits never jump by themselves`() {
        assertFalse(ProfileSkipPolicy.enabled("outro", intro = true, recap = true))
    }

    @Test fun `unknown segments and an unconfigured profile never skip`() {
        assertFalse(ProfileSkipPolicy.enabled("preview", intro = true, recap = true))
        for (type in listOf("intro", "recap", "outro")) {
            assertFalse(ProfileSkipPolicy.enabled(type, intro = false, recap = false))
        }
    }
}
