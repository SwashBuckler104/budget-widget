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

/** Periodic and on-demand inbox scan; with [backfillInput] it imports older messages instead. */
@HiltWorker
internal class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconciler: InboxReconciler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val backfillSince = inputData.getLong(KEY_BACKFILL_SINCE, NO_BACKFILL)
        return try {
            if (backfillSince == NO_BACKFILL) reconciler.reconcile() else reconciler.backfill(backfillSince)
            Result.success()
        } catch (_: SQLException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_BACKFILL_SINCE = "backfillSince"
        private const val NO_BACKFILL = Long.MIN_VALUE
        private const val MAX_ATTEMPTS = 3

        fun backfillInput(sinceMillis: Long): Data = workDataOf(KEY_BACKFILL_SINCE to sinceMillis)
    }
}
