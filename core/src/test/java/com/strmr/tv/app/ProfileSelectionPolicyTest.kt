package com.strmr.tv.app

import com.strmr.tv.api.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSelectionPolicyTest {
    private val adult = Profile(id="adult", name="Adult", hasPin=true)
    private val child = Profile(id="child", name="Child", hasPin=false)

    @Test fun `a remembered adult or launcher shortcut returns to family selection`() {
        assertNull(ProfileSelectionPolicy.startup(listOf(adult,child),adult.id,null))
        assertNull(ProfileSelectionPolicy.startup(listOf(adult,child),null,adult.id))
        assertNull(ProfileSelectionPolicy.startup(listOf(adult),adult.id,adult.id))
    }

    @Test fun `only profiles returned for the current account can open`() {
        assertNull(ProfileSelectionPolicy.startup(listOf(adult,child),"other-account-profile",null))
        assertNull(ProfileSelectionPolicy.startup(listOf(adult,child),null,"other-account-profile"))
        assertEquals(child,ProfileSelectionPolicy.startup(listOf(adult,child),child.id,null))
        assertEquals(child,ProfileSelectionPolicy.startup(listOf(child),null,null))
    }
    @Test fun `an adult profile with a remembered automatic choice still requires its PIN`() {
        assertFalse(ProfileSelectionPolicy.canOpenAutomatically(Profile(id="adult", name="Adult", hasPin=true)))
    }

    @Test fun `a PIN-free child profile can still be opened without a prompt`() {
        assertTrue(ProfileSelectionPolicy.canOpenAutomatically(Profile(id="child", name="Child", hasPin=false)))
    }
}
