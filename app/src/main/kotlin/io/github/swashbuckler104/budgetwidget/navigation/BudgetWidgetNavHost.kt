package io.github.swashbuckler104.budgetwidget.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.swashbuckler104.budgetwidget.R
import io.github.swashbuckler104.budgetwidget.feature.analysis.AnalysisRoute
import io.github.swashbuckler104.budgetwidget.feature.categories.CategoriesRoute
import io.github.swashbuckler104.budgetwidget.feature.transactions.debug.DebugTransactionsRoute
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

@Serializable
data object AnalysisDestination

@Serializable
data object TransactionsDestination

@Serializable
data object CategoriesDestination

private enum class Tab(val route: Any, val routeClass: KClass<*>, @StringRes val label: Int, val icon: ImageVector) {
    ANALYSIS(AnalysisDestination, AnalysisDestination::class, R.string.tab_analysis, Icons.Rounded.Insights),
    TRANSACTIONS(
        TransactionsDestination,
        TransactionsDestination::class,
        R.string.tab_transactions,
        Icons.AutoMirrored.Rounded.ReceiptLong,
    ),
    CATEGORIES(CategoriesDestination, CategoriesDestination::class, R.string.tab_categories, Icons.Rounded.Category),
}

/** The three tabs with bottom navigation (SCOPE.md F7). */
@Composable
fun BudgetWidgetNavHost(onOpenQuickSort: (transactionId: Long?) -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = backStack?.destination?.hasRoute(tab.routeClass) == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TransactionsDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable<AnalysisDestination> { AnalysisRoute(onSortInbox = { onOpenQuickSort(null) }) }
            composable<TransactionsDestination> { DebugTransactionsRoute(onOpenQuickSort = onOpenQuickSort) }
            composable<CategoriesDestination> { CategoriesRoute() }
        }
    }
}
