package io.github.swashbuckler104.budgetwidget

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BudgetWidgetTheme
import io.github.swashbuckler104.budgetwidget.navigation.BudgetWidgetNavHost
import io.github.swashbuckler104.budgetwidget.navigation.DeepLinkRequest

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Deep links delivered to the running activity (Insight widget taps). */
    private var deepLink by mutableStateOf<DeepLinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BudgetWidgetTheme {
                BudgetWidgetNavHost(
                    onOpenQuickSort = { id -> startActivity(QuickSortActivity.intent(this, id)) },
                    deepLink = deepLink,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { deepLink = DeepLinkRequest(it, SystemClock.elapsedRealtimeNanos()) }
    }
}
