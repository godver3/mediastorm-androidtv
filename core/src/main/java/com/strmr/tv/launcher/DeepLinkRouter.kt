package com.strmr.tv.launcher

import com.strmr.tv.app.AppLog
import com.strmr.tv.library.ProfileDataContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds a launcher link until the signed-in shell can open it (a cold start, sign-in, the
 * profile picker), as the tvOS `DeepLinkRouter` does. The shell takes it once a profile is in
 * use; a link from another profile's rows is dropped. Main thread only.
 */
data class PendingDeepLink(val link: DeepLink, val context: Long?, val revision: Long)

object DeepLinkRouter {
    private var revision = 0L
    private val _pending = MutableStateFlow<PendingDeepLink?>(null)
    val pending: StateFlow<PendingDeepLink?> = _pending

    /** The profile the pending link's rows belong to, so startup can open that profile. */
    val pendingProfileId: String? get() = _pending.value?.link?.profileId

    fun open(url: String?) {
        if (url == null) return
        if (url.startsWith(DeepLink.HOME_URL)) return AppLog.i(TAG, "opened from a channel")
        val link = DeepLink.parse(url) ?: return AppLog.w(TAG, "unhandled link $url")
        AppLog.i(TAG, "${link.action.host} ${link.target.titleId} (profile ${link.profileId ?: "?"})")
        _pending.value = PendingDeepLink(link, null, ++revision)
    }

    /** Internal selection never survives a server/account/profile context change. */
    fun openInternal(link: DeepLink, context: Long): Boolean {
        if (context != ProfileDataContext.current) return false
        _pending.value = PendingDeepLink(link, context, ++revision)
        return true
    }

    /** The pending link when it belongs to [profileId] (or names none); a mismatched one is dropped. */
    fun take(profileId: String?): DeepLink? {
        val pending = _pending.value ?: return null
        _pending.value = null
        val link = pending.link
        if (pending.context != null && (pending.context != ProfileDataContext.current || link.profileId != profileId)) return null
        if (link.profileId != null && link.profileId != profileId) {
            AppLog.w(TAG, "dropped ${link.target.titleId}: from profile ${link.profileId}, not $profileId")
            return null
        }
        return link
    }

    private const val TAG = "DeepLink"
}
