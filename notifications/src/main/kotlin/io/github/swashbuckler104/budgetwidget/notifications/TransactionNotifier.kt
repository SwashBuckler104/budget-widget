package io.github.swashbuckler104.budgetwidget.notifications

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxListener
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.data.suggest.SuggestionEngine
import io.github.swashbuckler104.budgetwidget.core.data.suggest.SuggestionRequest
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One notification per pending transaction, grouped under a summary once there are two or more
 * (SCOPE.md F3). Each notification is tagged `txn:<id>`, so it can be found and cancelled when
 * the transaction is sorted anywhere: sheet, widget or a notification button.
 */
@Singleton
internal class TransactionNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val suggestions: SuggestionEngine,
    private val settings: SettingsStore,
    private val factory: TransactionNotificationFactory,
    private val clock: WallClock,
) : InboxListener {
    private val manager = NotificationManagerCompat.from(context)

    /** Serializes post/cancel so the summary count is computed from a settled set. */
    private val mutex = Mutex()

    override suspend fun onPendingAdded(transactionId: Long, origin: IngestOrigin) {
        val transaction = transactions.get(transactionId) ?: return
        if (transaction.status != TransactionStatus.PENDING) return
        val notify = TransactionNotificationPolicy.shouldNotify(
            origin = origin,
            amountPaise = transaction.amountPaise,
            occurredAt = transaction.occurredAt,
            nowMillis = clock.nowMillis(),
            settings = settings.notifications.first(),
        )
        if (notify) post(transaction, silent = false)
    }

    override suspend fun onSorted(transactionId: Long) = cancel(transactionId)

    /** Re-posts [transactionId] quietly with fresh buttons (a button's category went away). */
    suspend fun refresh(transactionId: Long) {
        val transaction = transactions.get(transactionId)
        if (transaction?.status ==
            TransactionStatus.PENDING
        ) {
            post(transaction, silent = true)
        } else {
            cancel(transactionId)
        }
    }

    suspend fun cancel(transactionId: Long) = mutex.withLock {
        manager.cancel(tag(transactionId), NOTIFICATION_ID)
        updateSummary(shown = shownIds() - transactionId)
    }

    /** Removes every transaction notification (the user turned them off). */
    suspend fun cancelAll() = mutex.withLock {
        shownIds().forEach { manager.cancel(tag(it), NOTIFICATION_ID) }
        manager.cancel(SUMMARY_ID)
    }

    /** Removes notifications whose transaction is no longer pending, e.g. after a crash mid-sort. */
    suspend fun pruneStale() {
        val pending = transactions.pendingIds()
        shownIds().filterNot { it in pending }.forEach { cancel(it) }
    }

    private suspend fun post(transaction: Transaction, silent: Boolean) {
        if (!canPost()) return
        val chips = suggestions.suggest(
            SuggestionRequest(
                transaction.merchantKey,
                transaction.direction,
                transaction.amountPaise,
                transaction.parserType,
            ),
            limit = MAX_ACTIONS,
        )
        val duplicateOf = transaction.duplicateOfId?.let { transactions.get(it) }
        val notification = factory.transaction(
            transaction = transaction,
            actions = quickActions(chips, possibleDuplicate = duplicateOf != null),
            duplicateOf = duplicateOf,
            silent = silent,
        )
        mutex.withLock {
            if (notify(tag(transaction.id), NOTIFICATION_ID, notification)) {
                updateSummary(shown = shownIds() + transaction.id)
            }
        }
    }

    /**
     * [shown]: the transaction notifications that are up once the current change lands. The
     * system's list can lag a just-posted or just-cancelled notification, so callers pass it.
     */
    private fun updateSummary(shown: Set<Long>) {
        if (shown.size >= 2) notify(null, SUMMARY_ID, factory.summary(shown.size)) else manager.cancel(SUMMARY_ID)
    }

    private fun shownIds(): Set<Long> = manager.activeNotifications.mapNotNullTo(mutableSetOf()) { idFromTag(it.tag) }

    private fun canPost(): Boolean = manager.areNotificationsEnabled() && hasPermission()

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    /** Returns false when the permission is missing (revoked between check and post). */
    private fun notify(tag: String?, id: Int, notification: Notification): Boolean {
        if (!hasPermission()) return false
        return try {
            manager.notify(tag, id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        private const val TAG_PREFIX = "txn:"
        private const val NOTIFICATION_ID = 1
        private const val SUMMARY_ID = 2001

        fun tag(transactionId: Long) = "$TAG_PREFIX$transactionId"

        fun idFromTag(tag: String?): Long? = tag?.takeIf {
            it.startsWith(TAG_PREFIX)
        }?.removePrefix(TAG_PREFIX)?.toLongOrNull()
    }
}
