package io.github.swashbuckler104.budgetwidget.core.data.ingest

/** Starts inbox scans. Implemented in `:ingest` with WorkManager. */
interface SmsSync {
    /** Schedules the periodic safety-net scan (idempotent; safe to call on every start). */
    fun schedulePeriodicReconcile()

    /** Scans the inbox from the checkpoint now. */
    fun reconcileNow()

    /** Imports inbox messages received after [sinceMillis] (epoch ms, UTC), flagged as backfill. */
    fun backfill(sinceMillis: Long)
}
