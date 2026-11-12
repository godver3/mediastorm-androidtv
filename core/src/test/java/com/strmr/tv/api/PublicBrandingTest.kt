package com.strmr.tv.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Admin-uploaded branding paths resolve under the server's `/api` prefix. */
class PublicBrandingTest {
    private val server = "http://192.168.1.10:7777/"

    @Test fun uploadedPathResolvesUnderApi() {
        assertEquals(
            "http://192.168.1.10:7777/api/branding/images/loading-logo?v=12",
            PublicBranding.resolve("/branding/images/loading-logo?v=12", server),
        )
    }

    @Test fun keepsServerSubPathAndExistingApiPrefix() {
        assertEquals(
            "https://host.example/mediastorm/api/branding/images/home-tv",
            PublicBranding.resolve("/branding/images/home-tv", "https://host.example/mediastorm/"),
        )
        assertEquals(
            "http://192.168.1.10:7777/api/branding/images/home-tv",
            PublicBranding.resolve("/api/branding/images/home-tv", server),
        )
    }

    @Test fun absoluteUrlAndBlankPaths() {
        assertEquals("https://cdn.example/logo.png", PublicBranding.resolve("https://cdn.example/logo.png", server))
        assertNull(PublicBranding.resolve("  ", server))
        assertNull(PublicBranding.resolve(null, server))
    }

    @Test fun profilePickerPrefersTvImageOnTvAndMobileLogoOnPhone() {
        val both = PublicBranding(loadingLogoUrl = "/l", homeMobileLogoUrl = "/m", homeTvImageUrl = "/t")
        assertEquals("/t", both.profilePickerPath(tv = true))
        assertEquals("/m", both.profilePickerPath(tv = false))
        assertEquals("/l", both.logoPath)
        assertEquals("/m", PublicBranding(homeMobileLogoUrl = "/m", homeTvImageUrl = " ").profilePickerPath(tv = true))
        assertEquals("/t", PublicBranding(homeTvImageUrl = "/t").profilePickerPath(tv = false))
    }

    @Test fun resolvedAgainstMakesEveryPathAbsolute() {
        val resolved = PublicBranding(loadingLogoUrl = "/branding/images/loading-logo", homeTvImageUrl = "").resolvedAgainst(server)
        assertEquals("http://192.168.1.10:7777/api/branding/images/loading-logo", resolved.loadingLogoUrl)
        assertNull(resolved.homeTvImageUrl)
    }
}
