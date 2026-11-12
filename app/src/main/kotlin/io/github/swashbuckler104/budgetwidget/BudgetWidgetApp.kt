package io.github.swashbuckler104.budgetwidget

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExperimentalConfigurationApi
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
        @OptIn(ExperimentalConfigurationApi::class)
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            // Android 16 ignores JobInfo's important-while-foreground flag and logs a warning for
            // every job that requests it; older releases still honor it.
            .setMarkingJobsAsImportantWhileForeground(Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA)
            .build()

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
