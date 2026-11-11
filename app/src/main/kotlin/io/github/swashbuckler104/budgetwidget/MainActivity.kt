package io.github.swashbuckler104.budgetwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BudgetWidgetTheme
import io.github.swashbuckler104.budgetwidget.feature.onboarding.OnboardingRoute
import io.github.swashbuckler104.budgetwidget.navigation.BudgetWidgetNavHost
import io.github.swashbuckler104.budgetwidget.navigation.DeepLinkRequest
import io.github.swashbuckler104.budgetwidget.widget.QuickSortWidgetReceiver

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** Deep links delivered to the running activity (Insight widget taps). */
    private var deepLink by mutableStateOf<DeepLinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // Settings load in a few milliseconds; drawing nothing until then avoids a flash of
            // the wrong theme or of the main screen before setup.
            val ready = state as? MainUiState.Ready ?: return@setContent
            val dark = when (ready.appearance.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                // Status and navigation bar icons follow the app's theme, not only the system's.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            BudgetWidgetTheme(darkTheme = dark, dynamicColor = ready.appearance.dynamicColor) {
                if (ready.onboarding) {
                    OnboardingRoute(onFinish = {}, onPinWidget = pinWidget())
                } else {
                    BudgetWidgetNavHost(
                        onOpenQuickSort = { id -> startActivity(QuickSortActivity.intent(this, id)) },
                        deepLink = deepLink,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { deepLink = DeepLinkRequest(it, SystemClock.elapsedRealtimeNanos()) }
    }

    /** Asks the launcher to add the Quick-Sort widget (SCOPE.md F1 step 8); null if it can't. */
    private fun pinWidget(): (() -> Unit)? {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) return null
        return { manager.requestPinAppWidget(ComponentName(this, QuickSortWidgetReceiver::class.java), null, null) }
    }

    private companion object {
        // androidx.activity's defaults for a translucent three-button navigation bar.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
