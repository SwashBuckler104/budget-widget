package io.github.swashbuckler104.budgetwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BudgetWidgetTheme
import io.github.swashbuckler104.budgetwidget.feature.transactions.debug.DebugTransactionsRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BudgetWidgetTheme {
                // M1 debug screen; replaced by the three-tab nav host from M2 on.
                DebugTransactionsRoute()
            }
        }
    }
}
