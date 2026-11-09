package io.github.swashbuckler104.budgetwidget.ingest.work

import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestPipeline
import io.github.swashbuckler104.budgetwidget.core.datastore.IngestCheckpointStore
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Safety-net path (SCOPE.md F2): re-reads the inbox from the checkpoint minus an overlap and runs
 * every message through the same idempotent pipeline, so SMS missed by the receiver (process
 * killed, OEM battery savers, reboot) are still stored, and nothing is stored twice.
 */
internal class InboxReconciler @Inject constructor(
    private val inbox: SmsInboxSource,
    private val checkpoint: IngestCheckpointStore,
    private val pipeline: IngestPipeline,
    private val clock: WallClock,
) {
    sealed interface Result {
        data object NoPermission : Result

        data class Scanned(val messages: Int) : Result
    }

    suspend fun reconcile(): Result {
        val now = clock.nowMillis()
        val after = checkpoint.get()?.let { it - OVERLAP.inWholeMilliseconds }
            ?: (now - FIRST_RUN_LOOKBACK.inWholeMilliseconds)
        return scan(after, IngestOrigin.RECONCILE)
    }

    /** Imports messages after [sinceMillis] (epoch ms, UTC), flagged as backfill. */
    suspend fun backfill(sinceMillis: Long): Result = scan(sinceMillis, IngestOrigin.BACKFILL)

    private suspend fun scan(afterMillis: Long, origin: IngestOrigin): Result {
        if (!inbox.canRead()) return Result.NoPermission
        // A message dated in the future (clock changes) must not push the checkpoint past now.
        val ceiling = clock.nowMillis()
        var count = 0
        var newest = Long.MIN_VALUE
        inbox.forEachSince(afterMillis) { sms ->
            pipeline.ingest(sms, origin)
            count++
            newest = maxOf(newest, minOf(sms.receivedAt, ceiling))
            if (count % CHECKPOINT_EVERY == 0) checkpoint.advanceTo(newest)
        }
        if (count > 0) checkpoint.advanceTo(newest)
        return Result.Scanned(count)
    }

    companion object {
        /** SCOPE.md F2: re-read 10 minutes before the checkpoint. */
        val OVERLAP = 10.minutes

        /** Without a checkpoint, a normal scan looks back one day; older history needs a backfill. */
        val FIRST_RUN_LOOKBACK = 1.days

        private const val CHECKPOINT_EVERY = 100
    }
}
