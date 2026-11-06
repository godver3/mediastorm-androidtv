package com.strmr.tv.api

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

@Serializable
internal data class BadgeDisplayFields(val badgeVisibility: List<String>? = null)

@Serializable
private data class BadgeSettingsFields(val display: BadgeDisplayFields? = null)

/**
 * The card badges the server asks for (`display.badgeVisibility`), as the Expo app reads them:
 * the profile's list, else the server's; null when neither could be read.
 */
suspend fun ApiClient.badgeVisibility(): List<String>? = coroutineScope {
    val global = async {
        runCatching { send<BadgeSettingsFields>("GET", "/api/settings", promptOnLock = false, timeoutMs = 10_000) }.getOrNull()
    }
    val profile = runCatching {
        send<BadgeSettingsFields>("GET", "$profilePath/settings", promptOnLock = false, timeoutMs = 10_000)
    }.getOrNull()
    profile?.display?.badgeVisibility ?: global.await()?.display?.badgeVisibility
}
