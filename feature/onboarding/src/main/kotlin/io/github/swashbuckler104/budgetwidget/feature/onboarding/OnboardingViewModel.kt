package io.github.swashbuckler104.budgetwidget.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.datastore.IngestCheckpointStore
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** SCOPE.md F1 step 6: how much SMS history to import when setting up. */
enum class Backfill(val days: Int?) {
    DAYS_30(30),
    DAYS_90(90),

    /** Nothing older than now; tracking starts with the next SMS. */
    FRESH(null),
}

/** The steps of the setup flow (SCOPE.md F1), in order. */
enum class OnboardingStep { WELCOME, SMS, NOTIFICATIONS, BATTERY, HISTORY, CATEGORIES, WIDGET }

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val checkpoint: IngestCheckpointStore,
    private val sync: SmsSync,
    private val clock: WallClock,
    private val saved: SavedStateHandle,
    categories: CategoryRepository,
) : ViewModel() {
    val step: StateFlow<Int> = saved.getStateFlow(KEY_STEP, 0)
    val backfill: StateFlow<Backfill> = saved.getStateFlow(KEY_BACKFILL, Backfill.DAYS_90)

    /** For the category preview step. */
    val groups: StateFlow<List<CategoryGroup>> = categories.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun next() {
        saved[KEY_STEP] = (step.value + 1).coerceAtMost(OnboardingStep.entries.lastIndex)
    }

    fun back() {
        saved[KEY_STEP] = (step.value - 1).coerceAtLeast(0)
    }

    fun chooseBackfill(choice: Backfill) {
        saved[KEY_BACKFILL] = choice
    }

    /**
     * Ends setup: imports the chosen history (or starts tracking from now) and remembers that
     * setup is done. [smsGranted] false skips the import; Settings can run it later.
     */
    fun finish(smsGranted: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            val days = backfill.value.days
            when {
                days == null -> checkpoint.startAt(clock.nowMillis())
                smsGranted -> sync.backfill(clock.nowMillis() - days.days.inWholeMilliseconds)
            }
            settings.setOnboardingDone(true)
            onDone()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_STEP = "step"
        const val KEY_BACKFILL = "backfill"
    }
}
