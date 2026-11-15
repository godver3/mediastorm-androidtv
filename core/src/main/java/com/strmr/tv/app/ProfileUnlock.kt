package com.strmr.tv.app

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Profile
import com.strmr.tv.api.verifyPin
import com.strmr.tv.library.ProfileDataContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    private val prompts = ScopedProfileUnlock({ ProfileDataContext.current }) { pin,id,context,accept ->
        ApiClient.verifyPin(pin,id,expectedContext = context,accept = accept)
    }
    val pinChallenge = prompts.pinChallenge

    fun install(scope: CoroutineScope) {
        scope.launch { ProfileDataContext.changes.collect { prompts.expire() } }
    }
    suspend fun unlock(id: String, locked: Profile?, expectedContext: Long, onCancelled: suspend () -> Unit): Boolean =
        prompts.unlock(id,locked,expectedContext,onCancelled)

    suspend fun verify(pin: String, expected: AppSession.PinChallenge?): PinVerification = prompts.verify(pin,expected)
    fun cancel(expected: AppSession.PinChallenge?) = prompts.cancel(expected)
}
