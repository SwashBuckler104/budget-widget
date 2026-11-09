package io.github.swashbuckler104.budgetwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BudgetWidgetTheme
import io.github.swashbuckler104.budgetwidget.navigation.BudgetWidgetNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BudgetWidgetTheme {
                BudgetWidgetNavHost(onOpenQuickSort = { id -> startActivity(QuickSortActivity.intent(this, id)) })
            }
        }
    }
}
