package io.github.swashbuckler104.budgetwidget.notifications

import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import io.github.swashbuckler104.budgetwidget.core.datastore.NotificationSettings
import io.github.swashbuckler104.budgetwidget.core.model.Category
import kotlin.time.Duration.Companion.hours

/** Which new transactions get a notification (SCOPE.md F3). Money is paise, times epoch ms (UTC). */
internal object TransactionNotificationPolicy {
    /**
     * The inbox scan stores messages the SMS receiver missed; they still deserve a prompt, but not
     * once they are old news (for example after the phone was off for days).
     */
    val RECONCILE_MAX_AGE = 24.hours

    fun shouldNotify(
        origin: IngestOrigin,
        amountPaise: Long,
        occurredAt: Long,
        nowMillis: Long,
        settings: NotificationSettings,
    ): Boolean = settings.enabled &&
        amountPaise >= settings.minAmountPaise &&
        when (origin) {
            IngestOrigin.REALTIME -> true

            IngestOrigin.RECONCILE -> nowMillis - occurredAt <= RECONCILE_MAX_AGE.inWholeMilliseconds

            // Importing history must never produce a burst of notifications.
            IngestOrigin.BACKFILL -> false
        }
}

/** One button on a transaction notification. */
internal sealed interface QuickAction {
    data class Sort(val category: Category) : QuickAction

    data object Ignore : QuickAction

    /** Marks a possible cross-source duplicate as the same payment (SCOPE.md F2 "Merge"). */
    data object SamePayment : QuickAction
}

/** Android shows at most 3 notification actions. */
internal const val MAX_ACTIONS = 3

/**
 * SCOPE.md F3: the top suggestions, padded with "Ignore" when there are fewer than 3. A possible
 * duplicate leads with "Same payment" so it isn't counted twice by a quick tap.
 */
internal fun quickActions(suggestions: List<Category>, possibleDuplicate: Boolean): List<QuickAction> {
    val lead = if (possibleDuplicate) listOf(QuickAction.SamePayment) else emptyList()
    val actions = lead + suggestions.take(MAX_ACTIONS - lead.size).map { QuickAction.Sort(it) }
    return if (actions.size < MAX_ACTIONS) actions + QuickAction.Ignore else actions
}
