package io.github.swashbuckler104.budgetwidget.notifications

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** What a notification button asks for. Travels inside the action's broadcast intent. */
internal sealed interface NotificationCommand {
    val transactionId: Long

    data class Sort(override val transactionId: Long, val categoryId: Long) : NotificationCommand

    data class Ignore(override val transactionId: Long) : NotificationCommand

    data class SamePayment(override val transactionId: Long) : NotificationCommand

    companion object {
        private const val ACTION_SORT = "io.github.swashbuckler104.budgetwidget.notifications.SORT"
        private const val ACTION_IGNORE = "io.github.swashbuckler104.budgetwidget.notifications.IGNORE"
        private const val ACTION_SAME_PAYMENT = "io.github.swashbuckler104.budgetwidget.notifications.SAME_PAYMENT"
        private const val EXTRA_TRANSACTION = "transactionId"
        private const val EXTRA_CATEGORY = "categoryId"

        fun toIntent(context: Context, command: NotificationCommand): Intent {
            val (action, path) = when (command) {
                is Sort -> ACTION_SORT to "sort/${command.categoryId}"
                is Ignore -> ACTION_IGNORE to "ignore"
                is SamePayment -> ACTION_SAME_PAYMENT to "same-payment"
            }
            return Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                // PendingIntents that differ only in extras are the same PendingIntent: the data
                // URI keeps every transaction's buttons apart.
                .setData("budgetwidget://notification/${command.transactionId}/$path".toUri())
                .putExtra(EXTRA_TRANSACTION, command.transactionId)
                .apply { if (command is Sort) putExtra(EXTRA_CATEGORY, command.categoryId) }
        }

        fun fromIntent(intent: Intent): NotificationCommand? {
            val id = intent.getLongExtra(EXTRA_TRANSACTION, 0).takeIf { it > 0 } ?: return null
            return when (intent.action) {
                ACTION_SORT -> intent.getLongExtra(EXTRA_CATEGORY, 0).takeIf { it > 0 }?.let { Sort(id, it) }
                ACTION_IGNORE -> Ignore(id)
                ACTION_SAME_PAYMENT -> SamePayment(id)
                else -> null
            }
        }
    }
}
