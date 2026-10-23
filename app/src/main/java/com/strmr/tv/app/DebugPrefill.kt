package com.strmr.tv.app

import com.strmr.tv.BuildConfig

/**
 * Debug builds only: the secrets.properties test values that prefill the sign-in screens,
 * and `--ez autoSignIn true` on the launch intent, which walks the whole flow with them.
 * Release builds carry empty values, so nothing here applies.
 */
object DebugPrefill {
    val serverUrl: String? get() = BuildConfig.DEBUG_SERVER_URL.ifEmpty { null }.takeIf { BuildConfig.DEBUG }
    val username: String? get() = BuildConfig.DEBUG_USERNAME.ifEmpty { null }.takeIf { BuildConfig.DEBUG }
    val password: String? get() = BuildConfig.DEBUG_PASSWORD.ifEmpty { null }.takeIf { BuildConfig.DEBUG }
    val profileName: String? get() = BuildConfig.DEBUG_PROFILE_NAME.ifEmpty { null }.takeIf { BuildConfig.DEBUG }
    private val profilePin: String? get() = BuildConfig.DEBUG_PROFILE_PIN.ifEmpty { null }.takeIf { BuildConfig.DEBUG }

    suspend fun autoSignIn() {
        if (!BuildConfig.DEBUG) return
        val session = AppSession
        try {
            if (session.phase.value == AppSession.Phase.Ready) return
            if (session.phase.value == AppSession.Phase.Server) session.connect(serverUrl ?: return)
            if (session.phase.value == AppSession.Phase.Login) session.signIn(username ?: return, password ?: return)
            if (session.phase.value == AppSession.Phase.Profiles) {
                val match = session.profiles.value.firstOrNull { it.name == profileName } ?: return
                session.select(match, profilePin)
            }
        } catch (e: Exception) {
            AppLog.w("DebugPrefill", "auto sign-in failed: ${e.message}")
        }
    }
}
