package com.strmr.tv.api

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException

/** Retry a remote connection once, while retaining the request's captured identity. */
internal class ContextBoundTransport(
    private val context: RequestContext.Scope,
    private val remote: Boolean,
    private val recover: suspend () -> Boolean,
    private val onFailure: (IOException) -> Unit,
) {
    suspend fun <T> execute(send: suspend () -> T): T {
        context.check()
        try {
            val response=send();context.check();return response
        } catch (error: IOException) {
            context.check()
            if (!remote || !recoverable(error)) {
                context.use { onFailure(error) }
                throw error
            }
            val recovered=try {recover()}catch (failure: Exception){context.check();throw failure}
            context.check()
            if (!recovered) throw error
        }
        // A completed reconnect may have published a new origin. That requires
        // a fresh scoped request; an old prepared URL is never retargeted.
        context.check()
        return try {
            val response=send();context.check();response
        } catch (error: IOException) {
            context.use { onFailure(error) }
            throw error
        }
    }

    private fun recoverable(error: IOException) =
        error is ConnectException || error is SocketTimeoutException || error is NoRouteToHostException || error is SocketException ||
            (error is java.io.InterruptedIOException && error.message == "timeout") ||
            error.message?.contains("unexpected end of stream", ignoreCase = true) == true
}
