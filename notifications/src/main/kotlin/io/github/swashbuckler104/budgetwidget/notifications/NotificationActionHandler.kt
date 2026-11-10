package io.github.swashbuckler104.budgetwidget.notifications

import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortResult
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import javax.inject.Inject

/** Carries out a notification button tap (SCOPE.md F3). Works from a cold process. */
internal class NotificationActionHandler @Inject constructor(
    private val sort: SortTransactionUseCase,
    private val notifier: TransactionNotifier,
    private val widgets: InboxWidgets,
) {
    suspend fun handle(command: NotificationCommand) {
        val id = command.transactionId
        // requirePending: the notification may be older than a sort done in the app or widget.
        val result = when (command) {
            is NotificationCommand.Sort -> sort.categorize(id, command.categoryId, requirePending = true)
            is NotificationCommand.Ignore -> sort.ignore(id, IgnoreReason.USER, requirePending = true)
            is NotificationCommand.SamePayment -> sort.ignore(id, IgnoreReason.DUPLICATE, requirePending = true)
        }
        when (result) {
            // The sort reported itself to the InboxListeners, which cancelled the notification.
            SortResult.Done -> Unit

            SortResult.NotPending, SortResult.TransactionNotFound -> notifier.cancel(id)

            // The button's category was archived, deleted or got subcategories since posting.
            SortResult.CategoryNotFound, SortResult.CategoryArchived, SortResult.NotALeaf -> notifier.refresh(id)
        }
        // The process may die right after the broadcast: redraw widgets before returning.
        if (result == SortResult.Done) widgets.refreshNow()
    }
}
