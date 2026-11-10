package io.github.swashbuckler104.budgetwidget.notifications.day

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxListener
import io.github.swashbuckler104.budgetwidget.core.data.inbox.QuickSortLauncher
import io.github.swashbuckler104.budgetwidget.notifications.NotificationChannels
import io.github.swashbuckler104.budgetwidget.notifications.R
import javax.inject.Inject
import javax.inject.Singleton

/** The evening nudge (SCOPE.md F6): "3 transactions left to sort today", with "Sort now". */
@Singleton
internal class NudgeNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val days: DayCloseRepository,
    private val launcher: QuickSortLauncher,
) : InboxListener {
    private val manager = NotificationManagerCompat.from(context)

    /** Posts the nudge if anything that keeps today open is still waiting. */
    suspend fun nudge() {
        val status = days.status()
        if (status.open == 0) {
            manager.cancel(NUDGE_ID)
            return
        }
        val res = context.resources
        val text = if (status.streak > 0) {
            res.getQuantityString(R.plurals.notif_nudge_text_streak, status.streak, status.streak)
        } else {
            context.getString(R.string.notif_nudge_text)
        }
        val sort = PendingIntent.getActivity(
            context,
            0,
            launcher.inbox(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_transaction)
            .setContentTitle(res.getQuantityString(R.plurals.notif_nudge_title, status.open, status.open))
            .setContentText(text)
            .setContentIntent(sort)
            .addAction(0, context.getString(R.string.notif_nudge_action), sort)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        if (!hasPermission()) return
        try {
            manager.notify(NUDGE_ID, notification)
        } catch (_: SecurityException) {
            // Revoked between the check and the post: nothing to show.
        }
    }

    /** Clearing the inbox makes the nudge obsolete. */
    override suspend fun onSorted(transactionId: Long) {
        if (days.openCount() == 0) manager.cancel(NUDGE_ID)
    }

    private fun hasPermission(): Boolean = manager.areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            )

    companion object {
        const val NUDGE_ID = 2002
    }
}
