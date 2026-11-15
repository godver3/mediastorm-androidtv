package com.strmr.tv.api

/** The API client's authorization retry, isolated from Android persistence and transport. */
internal class RequestAuthorization(
    private val context: RequestContext.Scope,
    private val token: () -> String?,
    private val needsRefresh: () -> Boolean,
    private val refresh: suspend () -> ApiClient.RefreshOutcome,
    private val signOut: () -> Unit,
    private val send: suspend (String) -> String,
) {
    suspend fun execute(): String {
        if (context.use(needsRefresh) && rotate() == ApiClient.RefreshOutcome.REJECTED) reject()
        val initial = currentToken()
        return try {
            perform(initial)
        } catch (error: ApiError) {
            context.check()
            if (!error.invalidatesSession) throw error
            // Another request may already have rotated the rejected token.
            if (context.use(token) == initial) when (rotate()) {
                ApiClient.RefreshOutcome.REFRESHED -> Unit
                ApiClient.RefreshOutcome.FAILED -> throw error
                ApiClient.RefreshOutcome.REJECTED -> reject()
            }
            perform(currentToken())
        }
    }

    private fun currentToken(): String = context.use { token() ?: throw ApiError.SignedOut() }
    private fun reject(): Nothing {
        context.use(signOut)
        throw ApiError.SignedOut()
    }
    private suspend fun rotate(): ApiClient.RefreshOutcome {
        context.check()
        val outcome = refresh()
        context.check()
        return outcome
    }
    private suspend fun perform(value: String): String {
        context.check()
        val response = send(value)
        context.check()
        return response
    }
}
