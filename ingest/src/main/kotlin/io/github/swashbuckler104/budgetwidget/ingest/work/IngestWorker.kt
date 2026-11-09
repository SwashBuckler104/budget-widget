package io.github.swashbuckler104.budgetwidget.ingest.work

import android.content.Context
import android.database.SQLException
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestPipeline
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms

/** Runs one SMS from [SmsReceiver][io.github.swashbuckler104.budgetwidget.ingest.receiver.SmsReceiver] through the pipeline. */
@HiltWorker
internal class IngestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: IngestPipeline,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER)
        val body = inputData.getString(KEY_BODY)
        val receivedAt = inputData.getLong(KEY_RECEIVED_AT, -1)
        if (sender == null || body == null || receivedAt < 0) return Result.failure()
        return try {
            pipeline.ingest(RawSms(sender, body, receivedAt), IngestOrigin.REALTIME)
            Result.success()
        } catch (_: SQLException) {
            // Storage hiccup (disk full, locked): retry with backoff; reconcile is the final net.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    /** Expedited work runs as a foreground service below API 31, which needs a notification. */
    override suspend fun getForegroundInfo(): ForegroundInfo = SyncNotification.foregroundInfo(applicationContext)

    companion object {
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_RECEIVED_AT = "receivedAt"
        private const val MAX_ATTEMPTS = 5

        fun inputOf(sms: RawSms): Data = workDataOf(
            KEY_SENDER to sms.sender,
            KEY_BODY to sms.body,
            KEY_RECEIVED_AT to sms.receivedAt,
        )
    }
}
