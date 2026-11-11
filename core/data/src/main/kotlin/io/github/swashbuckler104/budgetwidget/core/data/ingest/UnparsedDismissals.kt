package io.github.swashbuckler104.budgetwidget.core.data.ingest

import io.github.swashbuckler104.budgetwidget.core.datastore.IngestCheckpointStore
import javax.inject.Inject

/**
 * Remembers which unparsed SMS the user deleted, as a time mark: the inbox scan re-reads a
 * 10-minute overlap (and backfill re-reads history), which would otherwise put deleted
 * messages straight back into the list. Messages received at or before the mark are not
 * recorded again; anything newer is.
 */
interface UnparsedDismissals {
    suspend fun upTo(): Long?

    suspend fun dismissUpTo(epochMillis: Long)

    /** Nothing is ever dismissed: for tests and callers without settings. */
    object None : UnparsedDismissals {
        override suspend fun upTo(): Long? = null

        override suspend fun dismissUpTo(epochMillis: Long) = Unit
    }
}

internal class StoredUnparsedDismissals @Inject constructor(private val store: IngestCheckpointStore) :
    UnparsedDismissals {
    override suspend fun upTo(): Long? = store.unparsedDismissedUpTo()

    override suspend fun dismissUpTo(epochMillis: Long) = store.dismissUnparsedUpTo(epochMillis)
}
