package io.github.swashbuckler104.budgetwidget.ingest.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import io.github.swashbuckler104.budgetwidget.ingest.R

/**
 * The silent notification Android requires while expedited work runs as a foreground service
 * (API 26-30 only; on API 31+ expedited work is a JobScheduler job and shows nothing).
 */
internal object SyncNotification {
    private const val CHANNEL_ID = "sync"
    private const val NOTIFICATION_ID = 1001

    fun foregroundInfo(context: Context): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.ingest_sync_channel),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(context.getString(R.string.ingest_sync_title))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
