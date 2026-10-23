package com.strmr.tv.app

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Profile
import com.strmr.tv.api.verifyPin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** Outcome of checking an entered PIN. */
sealed interface PinVerification {
    data object Unlocked : PinVerification
    data object Wrong : PinVerification
    /** Rate limited, network, etc.: the message is shown and the entry kept for a retry. */
    data class Failed(val message: String) : PinVerification
}

/**
 * `403 profile_locked` mid-flow (server-enforced PINs; the unlock expired or was revoked):
 * [AppSession] asks for the PIN over the current screen. Concurrent requests share one prompt.
 * Main thread only.
 */
internal object ProfileUnlock {
    val pinChallenge = MutableStateFlow<AppSession.PinChallenge?>(null)
    private var challenge: Pair<String, CompletableDeferred<Boolean>>? = null

    /**
     * Prompts for `locked` (null: no prompt, the call fails) and waits. Returns true once
     * unlocked; a request for another profile while one prompt is up fails. `onCancelled`
     * runs once, for the request that raised the prompt.
     */
    suspend fun unlock(id: String, locked: Profile?, onCancelled: suspend () -> Unit): Boolean {
        challenge?.let { (pending, deferred) -> return if (pending == id) deferred.await() else false }
        if (locked == null) return false
        val deferred = CompletableDeferred<Boolean>()
        challenge = id to deferred
        pinChallenge.value = AppSession.PinChallenge(locked)
        val unlocked = deferred.await()
        challenge = null
        if (!unlocked) onCancelled()
        return unlocked
    }

    /** Checks a PIN entered for the current challenge; on success the prompt closes and waiting requests retry. */
    suspend fun verify(pin: String): PinVerification {
        val current = pinChallenge.value ?: return PinVerification.Wrong
        try {
            if (!ApiClient.verifyPin(pin, current.profile.id)) return PinVerification.Wrong
        } catch (e: Exception) {
            return PinVerification.Failed(e.message ?: "Couldn't check the PIN.")
        }
        pinChallenge.value = null
        challenge?.second?.complete(true)
        return PinVerification.Unlocked
    }

    fun cancel() {
        pinChallenge.value = null
        challenge?.second?.complete(false)
    }
}
