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
import kotlinx.coroutines.flow.map
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
        settings.onboardingDone.map { done -> done ?: decideForUpgrade() },
    ) { appearance, done -> MainUiState.Ready(appearance, onboarding = !done) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    /**
     * Installs from before onboarding existed already have data: they skip setup. A fresh
     * install (nothing stored yet) goes through it.
     */
    private suspend fun decideForUpgrade(): Boolean {
        val upgraded = transactions.hasAny()
        if (upgraded) settings.setOnboardingDone(true)
        return upgraded
    }
}
