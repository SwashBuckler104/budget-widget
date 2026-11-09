package io.github.swashbuckler104.budgetwidget.ingest.work

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IngestWorkScheduler @Inject internal constructor(private val workManager: WorkManager) : SmsSync {
    /** Expedited so the transaction lands within seconds; normal work if the quota is used up. */
    fun enqueueIngest(sms: RawSms) {
        val request = OneTimeWorkRequestBuilder<IngestWorker>()
            .setInputData(IngestWorker.inputOf(sms))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_INGEST)
            .build()
        workManager.enqueue(request)
    }

    override fun schedulePeriodicReconcile() {
        val request = PeriodicWorkRequestBuilder<ReconcileWorker>(RECONCILE_INTERVAL_HOURS, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_RECONCILE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun reconcileNow() {
        val request = OneTimeWorkRequestBuilder<ReconcileWorker>().build()
        // KEEP: a scan that is already queued or running covers this request.
        workManager.enqueueUniqueWork(WORK_RECONCILE_NOW, ExistingWorkPolicy.KEEP, request)
    }

    override fun backfill(sinceMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ReconcileWorker>()
            .setInputData(ReconcileWorker.backfillInput(sinceMillis))
            .build()
        workManager.enqueueUniqueWork(WORK_BACKFILL, ExistingWorkPolicy.REPLACE, request)
    }

    internal companion object {
        const val TAG_INGEST = "ingest"
        const val WORK_RECONCILE_PERIODIC = "reconcile-periodic"
        const val WORK_RECONCILE_NOW = "reconcile-now"
        const val WORK_BACKFILL = "backfill"

        /** SCOPE.md F2: the safety-net scan runs every 3 h. */
        const val RECONCILE_INTERVAL_HOURS = 3L
        const val BACKOFF_SECONDS = 30L
    }
}
