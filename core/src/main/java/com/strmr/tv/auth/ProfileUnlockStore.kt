package com.strmr.tv.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Server-issued profile unlock tokens (`POST /api/users/{id}/pin/verify`), one per profile.
 * They are bound to the account session on the server, so they live in [SecureStore] next
 * to it, survive a token refresh unchanged and are dropped with it.
 */
class ProfileUnlockStore {
    @Serializable
    private data class Unlock(val token: String, val expiresAt: Long? = null) {
        val isExpired get() = expiresAt?.let { it <= System.currentTimeMillis() } ?: false
    }

    private val unlocks: MutableMap<String, Unlock> =
        SecureStore.read(ACCOUNT)
            ?.let { runCatching { Json.decodeFromString<Map<String, Unlock>>(it) }.getOrNull() }
            ?.toMutableMap() ?: mutableMapOf()

    init {
        if (unlocks.values.removeAll { it.isExpired }) save()
    }

    /** The live token for a profile, dropping it once expired. */
    @Synchronized
    fun token(profileId: String): String? {
        val unlock = unlocks[profileId] ?: return null
        if (unlock.isExpired) {
            remove(profileId)
            return null
        }
        return unlock.token
    }

    @Synchronized
    fun set(token: String, expiresAt: String?, profileId: String) {
        unlocks[profileId] = Unlock(token, expiresAt?.let { parseInstant(it) })
        save()
    }

    @Synchronized
    fun remove(profileId: String) {
        if (unlocks.remove(profileId) != null) save()
    }

    /** Profile switch: only the active profile stays unlocked. */
    @Synchronized
    fun keepOnly(profileId: String) {
        if (unlocks.keys.removeAll { it != profileId } || unlocks.values.removeAll { it.isExpired }) save()
    }

    @Synchronized
    fun removeAll() {
        unlocks.clear()
        SecureStore.delete(ACCOUNT)
    }

    private fun save() {
        if (unlocks.isEmpty()) SecureStore.delete(ACCOUNT)
        else SecureStore.write(ACCOUNT, Json.encodeToString(unlocks.toMap()))
    }

    private companion object {
        const val ACCOUNT = "profileUnlocks"
    }
}
