package io.github.swashbuckler104.budgetwidget

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import javax.inject.Inject

@HiltAndroidApp
class BudgetWidgetApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var smsSync: SmsSync

    // WorkManager initializes on demand with this config (its startup initializer is removed in
    // the manifest), so workers get Hilt dependencies.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Every process start, including one woken by an SMS after a reboot, re-arms the periodic
        // scan and catches up on anything missed (we have no boot receiver; ADR-0013).
        smsSync.schedulePeriodicReconcile()
        smsSync.reconcileNow()
    }
}
