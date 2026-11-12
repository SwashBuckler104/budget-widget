package io.github.swashbuckler104.budgetwidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BudgetWidgetTheme
import io.github.swashbuckler104.budgetwidget.feature.transactions.quicksort.QuickSortRoute
import io.github.swashbuckler104.budgetwidget.feature.transactions.quicksort.QuickSortViewModel
import javax.inject.Inject

/**
 * Transparent activity hosting the Quick-Sort sheet (SCOPE.md F3), so notifications and widgets can
 * open it over whatever is on screen. Intent extras reach the ViewModel's SavedStateHandle.
 */
@AndroidEntryPoint
class QuickSortActivity : ComponentActivity() {
    @Inject
    lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = null)
            val loaded = appearance ?: return@setContent
            val dark = when (loaded.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BudgetWidgetTheme(darkTheme = dark, dynamicColor = loaded.dynamicColor) {
                QuickSortRoute(onClose = ::finish)
            }
        }
    }

    companion object {
        /** [transactionId] null walks the inbox oldest first. */
        fun intent(context: Context, transactionId: Long?): Intent =
            Intent(context, QuickSortActivity::class.java).apply {
                transactionId?.let { putExtra(QuickSortViewModel.ARG_TRANSACTION_ID, it) }
            }
    }
}
