package com.strmr.tv.ui

import com.strmr.tv.api.ApiError
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.home.ServerSourceRecheckGateway
import com.strmr.tv.home.requestSourceRecheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The calling page owns the action after its menu closes. */
fun launchSourceRecheck(scope: CoroutineScope, target: DetailsTarget, episode: Episode? = null, onError: (String) -> Unit = AppToast::show) {
    val gateway = ServerSourceRecheckGateway()
    scope.launch {
        try {
            requestSourceRecheck(gateway, target, episode)
            AppToast.show("Source check requested")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!gateway.isCurrent()) return@launch
            onError(when ((error as? ApiError)?.statusCode) {
                404, 405, 501 -> "Source rechecks are unavailable on this server."
                409 -> "Reload the catalogue and check your source settings."
                429 -> "A source check was requested recently. Wait a minute and try again."
                403 -> "This profile cannot check these sources."
                else -> "Could not request a source check. Try again later."
            })
        }
    }
}
