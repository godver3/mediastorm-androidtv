package com.strmr.tv.launcher

import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.library.ProfileDataContext
import org.junit.Assert.*
import org.junit.Test

class InternalDeepLinkTest {
    private val link = DeepLink(DeepLink.Action.DETAILS,DetailsTarget("tmdb:movie:7","Synthetic",true),"profile")
    @Test fun `internal details and play routes are consumed only by their own current context`() {
        val context = ProfileDataContext.current
        assertTrue(DeepLinkRouter.openInternal(link,context)); assertEquals(link,DeepLinkRouter.take("profile")); assertNull(DeepLinkRouter.take("profile"))
        val play = link.copy(action = DeepLink.Action.PLAY); assertTrue(DeepLinkRouter.openInternal(play,context)); assertEquals(play,DeepLinkRouter.take("profile"))
    }
    @Test fun `pending internal selection is dropped after server account or profile epoch changes`() {
        val context = ProfileDataContext.current; assertTrue(DeepLinkRouter.openInternal(link,context)); ProfileDataContext.invalidate()
        assertNull(DeepLinkRouter.take("profile")); assertFalse(DeepLinkRouter.openInternal(link,context))
        assertTrue(DeepLinkRouter.openInternal(link,ProfileDataContext.current)); assertNull(DeepLinkRouter.take("other"))
    }
    @Test fun `repeated internal selections have distinct revisions`() {
        val context = ProfileDataContext.current
        DeepLinkRouter.openInternal(link,context); val first = DeepLinkRouter.pending.value!!.revision
        DeepLinkRouter.openInternal(link,context); assertTrue(DeepLinkRouter.pending.value!!.revision > first); DeepLinkRouter.take("profile")
    }
}
