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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import io.github.swashbuckler104.budgetwidget.R
import io.github.swashbuckler104.budgetwidget.feature.analysis.AnalysisRoute
import io.github.swashbuckler104.budgetwidget.feature.categories.CategoriesRoute
import io.github.swashbuckler104.budgetwidget.feature.settings.SettingsRoute
import io.github.swashbuckler104.budgetwidget.feature.transactions.list.TransactionsRoute
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

@Serializable
data object AnalysisDestination

@Serializable
data object TransactionsDestination

@Serializable
data object CategoriesDestination

@Serializable
data object SettingsDestination

/** Analysis drill-down: the transactions behind a category or merchant in one month. */
@Serializable
data class DrillDownDestination(
    val title: String,
    val fromMillis: Long,
    val toMillis: Long,
    val categoryId: Long = 0,
    val accountId: Long = 0,
    val query: String = "",
    val merchantKey: String = "",
    val direction: String = "",
)

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

/** Must match `InsightWidget.ANALYSIS_URI` in `:widget`. */
const val ANALYSIS_DEEP_LINK = "budgetwidget://analysis"

/** A deep link that arrived while the activity was running; [id] makes repeats distinct. */
data class DeepLinkRequest(val uri: String, val id: Long)

private fun NavController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** The three tabs with bottom navigation (SCOPE.md F7). */
@Composable
fun BudgetWidgetNavHost(
    onOpenQuickSort: (transactionId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    deepLink: DeepLinkRequest? = null,
) {
    val navController = rememberNavController()
    // A widget tap while the app is open: switch tabs like the bottom bar does, so the other tab
    // (an open edit sheet, typed input) keeps its state. A cold start goes through the NavHost's
    // own deep-link handling instead.
    LaunchedEffect(deepLink) {
        if (deepLink?.uri == ANALYSIS_DEEP_LINK) navController.navigateToTab(AnalysisDestination)
    }
    val backStack by navController.currentBackStackEntryAsState()
    val onTab =
        backStack?.destination?.let { destination -> Tab.entries.any { destination.hasRoute(it.routeClass) } } ?: true
    val openSettings = { navController.navigate(SettingsDestination) { launchSingleTop = true } }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = backStack?.destination?.hasRoute(tab.routeClass) == true,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = {
                                Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                        )
                    }
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
            // The Insight widget opens Analysis through this link (InsightWidget.ANALYSIS_URI).
            composable<AnalysisDestination>(
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = ANALYSIS_DEEP_LINK
                    },
                ),
            ) {
                AnalysisRoute(
                    onSortInbox = { onOpenQuickSort(null) },
                    onOpenSettings = openSettings,
                    onDrillDown = { d ->
                        navController.navigate(
                            DrillDownDestination(
                                d.title,
                                d.fromMillis,
                                d.toMillis,
                                d.categoryId,
                                d.accountId,
                                d.query,
                                d.merchantKey,
                                d.direction,
                            ),
                        )
                    },
                )
            }
            composable<DrillDownDestination> { entry ->
                TransactionsRoute(
                    onOpenQuickSort = onOpenQuickSort,
                    onOpenSettings = openSettings,
                    onBack = { navController.popBackStack() },
                    title = entry.toRoute<DrillDownDestination>().title,
                )
            }
            composable<TransactionsDestination> {
                TransactionsRoute(onOpenQuickSort = onOpenQuickSort, onOpenSettings = openSettings)
            }
            composable<CategoriesDestination> { CategoriesRoute() }
            composable<SettingsDestination> { SettingsRoute(onBack = { navController.popBackStack() }) }
        }
    }
}
