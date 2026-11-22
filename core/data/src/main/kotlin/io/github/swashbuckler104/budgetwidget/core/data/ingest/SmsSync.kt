package io.github.swashbuckler104.budgetwidget.core.data.ingest

/** Starts inbox scans. Implemented in `:ingest` with WorkManager. */
interface SmsSync {
    /** Schedules the periodic safety-net scan (idempotent; safe to call on every start). */
    fun schedulePeriodicReconcile()

    /** Scans the inbox from the checkpoint now. */
    fun reconcileNow()

    /**
     * Imports inbox messages received after [sinceMillis] (epoch ms, UTC), flagged as backfill.
     * With [fileUnderOther], the imported payments still in the inbox afterwards are filed under
     * Other in one go (ADR-0058).
     */
    fun backfill(sinceMillis: Long, fileUnderOther: Boolean = false)
}
