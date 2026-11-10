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

    /** Serializes every post/cancel and guards [shown]. */
    private val mutex = Mutex()

    /**
     * Transaction notifications we have up, by transaction id. Kept here rather than read back
     * from the system, whose list is updated asynchronously and can lag several calls in a burst.
     * Seeded from the system once per process; swipes report back through the delete intent.
     */
    private var shown: MutableSet<Long>? = null

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
        if (notify) post(transactionId, silent = false)
    }

    override suspend fun onSorted(transactionId: Long) = cancel(transactionId)

    /** Re-posts [transactionId] quietly with fresh buttons (a button's category went away). */
    suspend fun refresh(transactionId: Long) = post(transactionId, silent = true)

    suspend fun cancel(transactionId: Long) = mutex.withLock { cancelLocked(transactionId) }

    /** The user swiped the notification away: it is no longer up (the transaction stays pending). */
    suspend fun dismissed(transactionId: Long) = mutex.withLock {
        if (shownSet().remove(transactionId)) updateSummary()
    }

    /** Removes every transaction notification (the user turned them off). */
    suspend fun cancelAll() = mutex.withLock {
        val ids = shownSet()
        ids.forEach { manager.cancel(tag(it), NOTIFICATION_ID) }
        ids.clear()
        manager.cancel(SUMMARY_ID)
    }

    /** Removes notifications whose transaction is no longer pending, e.g. after a crash mid-sort. */
    suspend fun pruneStale() = mutex.withLock {
        val pending = transactions.pendingIds()
        shownSet().filterNot { it in pending }.forEach { cancelLocked(it) }
    }

    /**
     * Builds and posts [transactionId]'s notification. The status is checked under the lock, right
     * before posting, so a sort that lands meanwhile (whose cancel ran first) can't leave an orphan.
     */
    private suspend fun post(transactionId: Long, silent: Boolean) = mutex.withLock {
        val transaction = transactions.get(transactionId)
        if (transaction?.status != TransactionStatus.PENDING) {
            cancelLocked(transactionId)
            return@withLock
        }
        if (!canPost()) return@withLock
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
        if (notify(tag(transactionId), NOTIFICATION_ID, notification) && shownSet().add(transactionId)) updateSummary()
    }

    private fun cancelLocked(transactionId: Long) {
        manager.cancel(tag(transactionId), NOTIFICATION_ID)
        if (shownSet().remove(transactionId)) updateSummary()
    }

    private fun shownSet(): MutableSet<Long> = shown
        ?: manager.activeNotifications.mapNotNullTo(mutableSetOf()) { idFromTag(it.tag) }.also { shown = it }

    /** Called only when the set of shown notifications changed, so the summary is never re-posted needlessly. */
    private fun updateSummary() {
        val count = shownSet().size
        if (count >= 2) notify(null, SUMMARY_ID, factory.summary(count)) else manager.cancel(SUMMARY_ID)
    }

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
