package io.github.swashbuckler104.budgetwidget

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.swashbuckler104.budgetwidget.core.data.category.CategorySeeder
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class BudgetWidgetApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var smsSync: SmsSync

    @Inject
    lateinit var categorySeeder: CategorySeeder

    /** Lives as long as the process; for one-off startup work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        // Seed categories once (idempotent; deleted seeds stay deleted).
        appScope.launch { categorySeeder.ensureSeeded() }
    }
}
