package io.github.swashbuckler104.budgetwidget.core.data.inbox

import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin

/**
 * Per-transaction inbox events for code outside the data layer (notifications, SCOPE.md F3).
 * Modules contribute implementations to a Hilt set (`@IntoSet`). Calls happen after the database
 * write has committed, on the caller's coroutine; implementations must be quick and must not throw.
 */
interface InboxListener {
    /** [transactionId] was just stored as PENDING (it was not auto-sorted). */
    suspend fun onPendingAdded(transactionId: Long, origin: IngestOrigin) {}

    /** [transactionId] left the inbox: categorized, ignored or marked as a duplicate. */
    suspend fun onSorted(transactionId: Long) {}
}
