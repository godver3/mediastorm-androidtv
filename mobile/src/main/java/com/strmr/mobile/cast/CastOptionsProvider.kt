package com.strmr.mobile.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.strmr.mobile.MainActivity

/**
 * The Cast SDK's options (named in the manifest): Chromecasts able to run the Default Media
 * Receiver (`CC1AD845`, which the Expo and iPhone apps cast to), the receiver app stopped when the
 * session ends, and the SDK's media notification opening the app.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val notification = NotificationOptions.Builder().setTargetActivityClassName(MainActivity::class.java.name).build()
        val media = CastMediaOptions.Builder().setNotificationOptions(notification).build()
        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(media)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
