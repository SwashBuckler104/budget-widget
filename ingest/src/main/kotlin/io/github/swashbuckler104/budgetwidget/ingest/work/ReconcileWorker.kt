package io.github.swashbuckler104.budgetwidget.ingest.work

import android.content.Context
import android.database.SQLException
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsBodyRetention
import io.github.swashbuckler104.budgetwidget.core.data.sort.BulkSorter

/** Periodic and on-demand inbox scan; with [backfillInput] it imports older messages instead. */
@HiltWorker
internal class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconciler: InboxReconciler,
    private val widgets: InboxWidgets,
    private val retention: SmsBodyRetention,
    private val bulk: BulkSorter,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val backfillSince = inputData.getLong(KEY_BACKFILL_SINCE, NO_BACKFILL)
        return try {
            val result = if (backfillSince ==
                NO_BACKFILL
            ) {
                reconciler.reconcile()
            } else {
                reconciler.backfill(backfillSince)
            }
            // Onboarding's "file imported payments under Other" (ADR-0058), once they are all in.
            if (backfillSince != NO_BACKFILL && inputData.getBoolean(KEY_FILE_UNDER_OTHER, false)) {
                bulk.fileUnderOther(onlyBackfill = true)
            }
            if (result is InboxReconciler.Result.Scanned && result.inserted > 0) widgets.refreshNow()
            // "Keep original SMS text" (ADR-0045): runs even without SMS permission.
            retention.apply()
            Result.success()
        } catch (_: SQLException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_BACKFILL_SINCE = "backfillSince"
        private const val KEY_FILE_UNDER_OTHER = "fileUnderOther"
        private const val NO_BACKFILL = Long.MIN_VALUE
        private const val MAX_ATTEMPTS = 3

        fun backfillInput(sinceMillis: Long, fileUnderOther: Boolean = false): Data =
            workDataOf(KEY_BACKFILL_SINCE to sinceMillis, KEY_FILE_UNDER_OTHER to fileUnderOther)
    }
}
