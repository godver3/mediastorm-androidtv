package com.strmr.mobile.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.strmr.mobile.MainActivity
import com.strmr.tv.app.App

/**
 * The download notifications: the ongoing one the foreground service shows (what is running,
 * overall progress, or what the queue waits for) and a one-off for each finished or failed
 * download. Posting needs the notification permission on Android 13+; without it the
 * downloads still run, the system just doesn't show them.
 */
object DownloadNotifications {
    const val ONGOING_ID = 7301
    private const val CHANNEL_PROGRESS = "downloads.progress"
    private const val CHANNEL_DONE = "downloads.done"

    private val context: Context get() = App.context

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The service's notification for the queue as it stands. */
    fun ongoing(items: List<DownloadItem>): Notification {
        ensureChannels()
        val running = items.filter { it.status == DownloadItem.Status.DOWNLOADING }
        val queued = items.filter { it.status == DownloadItem.Status.PENDING }
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (running.isEmpty()) {
            val waiting = queued.firstOrNull()?.let(DownloadFormat::progressLine) ?: "Queued"
            return builder.setContentTitle(if (queued.size == 1) queued[0].displayTitle else "${queued.size} downloads queued")
                .setContentText(waiting).setProgress(0, 0, false).build()
        }
        val total = running.sumOf { it.expectedBytes }
        val written = running.sumOf { it.bytesWritten }
        val percent = if (total > 0) (written * 100 / total).toInt() else 0
        val title = if (running.size == 1) running[0].displayTitle else "Downloading ${running.size} items"
        val more = if (queued.isNotEmpty()) " · ${queued.size} queued" else ""
        val speed = DownloadFormat.speed(running.sumOf { it.bytesPerSecond })
        return builder.setContentTitle(title)
            .setContentText(listOfNotNull("$percent%", speed).joinToString(" · ") + more)
            .setProgress(100, percent, total <= 0)
            .build()
    }

    fun update(items: List<DownloadItem>) = post(ONGOING_ID, ongoing(items))

    fun completed(item: DownloadItem) = post(
        item.id.hashCode(),
        NotificationCompat.Builder(context, CHANNEL_DONE).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloaded").setContentText(item.displayTitle).setAutoCancel(true).setContentIntent(openApp()).build(),
    )

    fun failed(item: DownloadItem) = post(
        item.id.hashCode(),
        NotificationCompat.Builder(context, CHANNEL_DONE).setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed").setContentText(item.displayTitle + (item.errorMessage?.let { " · $it" } ?: ""))
            .setAutoCancel(true).setContentIntent(openApp()).build(),
    )

    private fun post(id: Int, notification: Notification) {
        ensureChannels()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
