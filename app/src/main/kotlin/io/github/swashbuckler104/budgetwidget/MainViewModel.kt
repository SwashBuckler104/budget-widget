package io.github.swashbuckler104.budgetwidget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.datastore.AppearanceSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** What the main activity shows: nothing until settings load, then setup or the app. */
sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(val appearance: AppearanceSettings, val onboarding: Boolean) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val transactions: TransactionRepository,
) : ViewModel() {
    val state: StateFlow<MainUiState> = combine(
        settings.appearance,
        // The first read settles a missing value once; later writes can't re-decide mid-setup.
        settings.onboardingDone.onStart { decideOnce() }.filterNotNull().distinctUntilChanged(),
    ) { appearance, done -> MainUiState.Ready(appearance, onboarding = !done) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    /**
     * Stores whether setup is needed the first time the app opens: installs from before
     * onboarding existed already have data and skip it; a fresh install goes through it. Storing
     * "not done" explicitly means an SMS imported or a setting written during setup can never
     * flip the answer and make setup vanish halfway (ADR-0027).
     */
    private suspend fun decideOnce() {
        if (settings.onboardingDone.first() != null) return
        settings.setOnboardingDone(transactions.hasAny())
    }
}
