package com.strmr.tv.api

import kotlinx.serialization.Serializable

// Auth / profiles

/** `POST /api/auth/login` and `POST /api/auth/refresh` (refresh rotates the token). */
@Serializable
data class LoginResponse(
    val token: String,
    val expiresAt: String? = null,
    val accountId: String,
    val username: String,
    val isMaster: Boolean? = null,
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val color: String? = null,
    val iconUrl: String? = null,
    val hasIcon: Boolean? = null,
    val hasPin: Boolean? = null,
    val pinLength: Int? = null,
    val isKidsProfile: Boolean? = null,
    /**
     * Kids profiles: "content_list" limits Home to [kidsAllowedLists] (MDBList URLs);
     * "rating" (or none) filters by rating server-side.
     */
    val kidsMode: String? = null,
    val kidsAllowedLists: List<String>? = null,
) {
    val requiresPin get() = hasPin == true
    val isKidsCurated get() = isKidsProfile == true && kidsMode == "content_list"
}

/** `GET /api/version` (public). */
@Serializable
data class ServerVersion(val version: String, val buildId: String? = null)

/** `GET /api/branding` (public). */
@Serializable
data class PublicBrandingResponse(val branding: PublicBranding? = null)

/** Branding images uploaded in the admin UI; paths are server-relative or absolute URLs. */
@Serializable
data class PublicBranding(
    val loadingLogoUrl: String? = null,
    val homeMobileLogoUrl: String? = null,
    val homeTvImageUrl: String? = null,
) {
    /** The wide logo the Expo app's sign-in shows (loading logo, else the mobile logo). */
    val logoPath: String?
        get() = first(loadingLogoUrl, homeMobileLogoUrl)

    /**
     * The profile picker's image, as in the Expo app: the TV home image on TV (else the
     * mobile logo), the mobile logo on phones and tablets (else the TV image).
     */
    fun profilePickerPath(tv: Boolean): String? =
        if (tv) first(homeTvImageUrl, homeMobileLogoUrl) else first(homeMobileLogoUrl, homeTvImageUrl)

    /** Every path resolved to an absolute URL on [server] (blank ones dropped). */
    fun resolvedAgainst(server: String): PublicBranding = PublicBranding(
        loadingLogoUrl = resolve(loadingLogoUrl, server),
        homeMobileLogoUrl = resolve(homeMobileLogoUrl, server),
        homeTvImageUrl = resolve(homeTvImageUrl, server),
    )

    companion object {
        /**
         * Resolves a branding path against the server. Uploads are stored as
         * `/branding/images/<slot>?v=…`, which the server serves under `/api`.
         */
        fun resolve(path: String?, server: String): String? {
            val trimmed = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                return trimmed
            }
            val relative = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
            return server.trimEnd('/') + if (relative.startsWith("/api/")) relative else "/api$relative"
        }

        private fun first(vararg paths: String?): String? =
            paths.mapNotNull { it?.trim() }.firstOrNull { it.isNotEmpty() }
    }
}
