package io.github.swashbuckler104.budgetwidget.core.data.inbox

import android.util.Log
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fans inbox events out to every [InboxListener]. A failing listener is logged and skipped: a
 * notification problem must never undo or block storing or sorting a transaction.
 */
class InboxEvents @Inject constructor(private val listeners: Set<@JvmSuppressWildcards InboxListener>) {
    suspend fun pendingAdded(transactionId: Long, origin: IngestOrigin) =
        each { it.onPendingAdded(transactionId, origin) }

    suspend fun sorted(transactionId: Long) = each { it.onSorted(transactionId) }

    suspend fun dataReplaced() = each { it.onDataReplaced() }

    private suspend fun each(block: suspend (InboxListener) -> Unit) {
        for (listener in listeners) {
            try {
                block(listener)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // Never log transaction details (PRIVACY.md): the class name is enough to debug.
                Log.w(TAG, "${listener::class.java.simpleName} failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "InboxEvents"
    }
}
