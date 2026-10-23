package com.strmr.tv.launcher

import com.strmr.tv.app.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds a launcher link until the signed-in shell can open it (a cold start, sign-in, the
 * profile picker), as the tvOS `DeepLinkRouter` does. The shell takes it once a profile is in
 * use; a link from another profile's rows is dropped. Main thread only.
 */
object DeepLinkRouter {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending

    /** The profile the pending link's rows belong to, so startup can open that profile. */
    val pendingProfileId: String? get() = _pending.value?.profileId

    fun open(url: String?) {
        if (url == null) return
        if (url.startsWith(DeepLink.HOME_URL)) return AppLog.i(TAG, "opened from a channel")
        val link = DeepLink.parse(url) ?: return AppLog.w(TAG, "unhandled link $url")
        AppLog.i(TAG, "${link.action.host} ${link.target.titleId} (profile ${link.profileId ?: "?"})")
        _pending.value = link
    }

    /** The pending link when it belongs to [profileId] (or names none); a mismatched one is dropped. */
    fun take(profileId: String?): DeepLink? {
        val link = _pending.value ?: return null
        _pending.value = null
        if (link.profileId != null && link.profileId != profileId) {
            AppLog.w(TAG, "dropped ${link.target.titleId}: from profile ${link.profileId}, not $profileId")
            return null
        }
        return link
    }

    private const val TAG = "DeepLink"
}
