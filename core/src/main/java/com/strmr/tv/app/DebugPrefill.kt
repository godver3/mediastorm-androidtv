package com.strmr.tv.app


/**
 * Debug builds only: the secrets.properties test values that prefill the sign-in screens,
 * and `--ez autoSignIn true` on the launch intent, which walks the whole flow with them.
 * Release builds carry empty values, so nothing here applies.
 */
object DebugPrefill {
    val serverUrl: String? get() = AppBuild.DEBUG_SERVER_URL.ifEmpty { null }.takeIf { AppBuild.DEBUG }
    val username: String? get() = AppBuild.DEBUG_USERNAME.ifEmpty { null }.takeIf { AppBuild.DEBUG }
    val password: String? get() = AppBuild.DEBUG_PASSWORD.ifEmpty { null }.takeIf { AppBuild.DEBUG }
    val profileName: String? get() = AppBuild.DEBUG_PROFILE_NAME.ifEmpty { null }.takeIf { AppBuild.DEBUG }
    private val profilePin: String? get() = AppBuild.DEBUG_PROFILE_PIN.ifEmpty { null }.takeIf { AppBuild.DEBUG }

    suspend fun autoSignIn() {
        if (!AppBuild.DEBUG) return
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
