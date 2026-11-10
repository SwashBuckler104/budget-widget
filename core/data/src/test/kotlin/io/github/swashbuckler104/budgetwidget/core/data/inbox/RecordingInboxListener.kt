package io.github.swashbuckler104.budgetwidget.core.data.inbox

import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin

/** Records inbox events in order, for assertions. */
internal class RecordingInboxListener : InboxListener {
    val events = mutableListOf<String>()

    override suspend fun onPendingAdded(transactionId: Long, origin: IngestOrigin) {
        events += "added:$transactionId:$origin"
    }

    override suspend fun onSorted(transactionId: Long) {
        events += "sorted:$transactionId"
    }
}
