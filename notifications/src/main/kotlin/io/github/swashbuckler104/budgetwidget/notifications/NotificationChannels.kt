package io.github.swashbuckler104.budgetwidget.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Our notification channels. Creating an existing channel is a no-op that keeps the user's changes. */
internal object NotificationChannels {
    /** SCOPE.md F3: "New transactions", importance HIGH by default (the user can lower it). */
    const val TRANSACTIONS = "transactions"

    /** SCOPE.md F6: the evening nudge. Default importance: it may sound, but never pops up. */
    const val REMINDERS = "reminders"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                TRANSACTIONS,
                context.getString(R.string.notif_channel_transactions),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.notif_channel_transactions_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDERS,
                context.getString(R.string.notif_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_reminders_description) },
        )
    }
}
