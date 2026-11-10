package io.github.swashbuckler104.budgetwidget.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.swashbuckler104.budgetwidget.core.data.inbox.QuickSortLauncher
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import java.util.Date
import javax.inject.Inject

/** Builds transaction notifications (SCOPE.md F3). Holds no state. */
internal class TransactionNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val launcher: QuickSortLauncher,
) {
    /**
     * Title `-₹450 · SWIGGY`, text `HDFC ••1234 · 8:42 PM`. The sign is spelled out because color
     * must never be the only signal (SCOPE.md §6). [duplicateOf] is the transaction this one may
     * repeat. [silent] updates an existing notification without sound or vibration.
     */
    fun transaction(
        transaction: Transaction,
        actions: List<QuickAction>,
        duplicateOf: Transaction?,
        silent: Boolean,
    ): Notification {
        val id = transaction.id
        val text = listOfNotNull(account(transaction), time(transaction.occurredAt)).joinToString(" · ")
        val hint = duplicateOf?.let {
            context.getString(R.string.notif_duplicate_hint, formatInr(it.amountPaise), time(it.occurredAt))
        }
        val builder = NotificationCompat.Builder(context, NotificationChannels.TRANSACTIONS)
            .setSmallIcon(R.drawable.ic_stat_transaction)
            .setContentTitle(title(transaction))
            .setContentText(text)
            .setColor(if (transaction.direction == Direction.IN) COLOR_IN else COLOR_OUT)
            .setWhen(transaction.occurredAt)
            // The text already says when; the header would repeat it as "now".
            .setShowWhen(false)
            .setGroup(GROUP)
            .setSortKey(transaction.occurredAt.toString().padStart(SORT_KEY_DIGITS, '0'))
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            // Stays until the transaction is sorted anywhere (the listener cancels it then).
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion())
            .setContentIntent(activity(launcher.transaction(context, id)))
        hint?.let { builder.setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$it")) }
        actions.forEach { action ->
            val (label, command) = when (action) {
                is QuickAction.Sort ->
                    "${action.category.emoji} ${action.category.name}" to
                        NotificationCommand.Sort(id, action.category.id)

                QuickAction.Ignore -> context.getString(R.string.notif_action_ignore) to NotificationCommand.Ignore(id)

                QuickAction.SamePayment ->
                    context.getString(R.string.notif_action_same_payment) to NotificationCommand.SamePayment(id)
            }
            builder.addAction(0, label, broadcast(command))
        }
        return builder.build()
    }

    /** The group summary shown once two or more transaction notifications are up. */
    fun summary(count: Int): Notification {
        val title = context.resources.getQuantityString(R.plurals.notif_summary_title, count, count)
        return NotificationCompat.Builder(context, NotificationChannels.TRANSACTIONS)
            .setSmallIcon(R.drawable.ic_stat_transaction)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_summary_text))
            .setGroup(GROUP)
            .setGroupSummary(true)
            // Each transaction alerts on its own; the summary never adds a second sound.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion())
            .setContentIntent(activity(launcher.inbox(context)))
            .build()
    }

    private fun title(transaction: Transaction): String {
        val sign = if (transaction.direction == Direction.IN) "+" else "-"
        return listOfNotNull(sign + formatInr(transaction.amountPaise), transaction.merchantDisplay).joinToString(" · ")
    }

    private fun account(transaction: Transaction): String? =
        listOfNotNull(transaction.bankName, transaction.accountLast4?.let { "••$it" })
            .joinToString(" ")
            .ifEmpty { null }

    private fun time(epochMillis: Long): String = DateFormat.getTimeFormat(context).format(Date(epochMillis))

    /** What the lock screen shows: no amounts, merchants or accounts. */
    private fun publicVersion(): Notification = NotificationCompat.Builder(context, NotificationChannels.TRANSACTIONS)
        .setSmallIcon(R.drawable.ic_stat_transaction)
        .setContentTitle(context.getString(R.string.notif_public_title))
        .build()

    private fun activity(intent: android.content.Intent): PendingIntent =
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun broadcast(command: NotificationCommand): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        NotificationCommand.toIntent(context, command),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val GROUP = "transactions"

        /** Material red 800 / green 800: readable on light and dark notification shades. */
        private const val COLOR_OUT = 0xFFC62828.toInt()
        private const val COLOR_IN = 0xFF2E7D32.toInt()
        private const val SORT_KEY_DIGITS = 19
    }
}
