package io.github.swashbuckler104.budgetwidget.feature.transactions.list

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.list.DayGroup
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val food = Category(5, null, "Groceries", "🛒", 0, CategoryKind.EXPENSE, 0, archived = false, isSeed = true)

    private fun tx(
        id: Long,
        merchant: String,
        status: TransactionStatus,
        amount: Long = 450_00,
        category: Long? = null,
    ) = Transaction(
        id = id, source = TransactionSource.SMS, amountPaise = amount, direction = Direction.OUT, parserType = null,
        merchantDisplay = merchant, bankName = "Axis Bank", accountLast4 = "2207", occurredAt = 0, isCard = false,
        status = status, isBackfill = false, duplicateOfId = null, categoryId = category,
    )

    private val calls = mutableListOf<String>()

    private fun callbacks() = ScreenCallbacks(
        onBack = null,
        onFilter = { calls += "filter" },
        onClearFilters = { calls += "clear" },
        onOpenQuickSort = { calls += "quicksort:$it" },
        onOpenSettings = { calls += "settings" },
        onEdit = { calls += "edit:$it" },
        onToggleSelection = { calls += "select:$it" },
        onSelectAll = { calls += "selectAll" },
        onClearSelection = { calls += "clearSelection" },
        onCategorize = { ids, bulk -> calls += "categorize:$ids:$bulk" },
        onIgnore = { calls += "ignore:$it" },
        onUnignore = { calls += "unignore:$it" },
        onMerge = { calls += "merge:$it" },
        onKeepBoth = { calls += "keepBoth:$it" },
        onAdd = { calls += "add" },
        onRequestSms = { calls += "sms" },
        onSyncNow = { calls += "sync" },
        onBackfill = { calls += "backfill" },
    )

    private fun show(state: TransactionsUiState) = compose.setContent {
        TransactionsScreen(
            state,
            title = null,
            snackbar = SnackbarHostState(),
            smsGranted = true,
            callbacks = callbacks(),
        )
    }

    @Test
    fun `inbox is pinned above the day list and its actions reach the callbacks`() {
        val original = tx(1, "Jupiter", TransactionStatus.CATEGORIZED, category = food.id)
        val dup = tx(2, "Federal", TransactionStatus.PENDING).copy(duplicateOfId = 1)
        show(
            TransactionsUiState(
                loading = false,
                inbox = listOf(InboxEntry(dup, overdue = true, duplicateOf = original)),
                days = listOf(DayGroup(LocalDate.of(2026, 1, 1), listOf(original), 450_00, 0)),
                categories = mapOf(food.id to food),
                total = 2,
            ),
        )
        compose.onNodeWithText("Inbox · 1 to sort").assertExists()
        compose.onNodeWithText("Overdue").assertExists()
        compose.onNodeWithText("Looks like the same payment as ₹450", substring = true).assertExists()

        compose.onNodeWithText("Federal").performClick()
        compose.onNodeWithText("Merge").performClick()
        compose.onNodeWithText("Keep both").performClick()
        compose.onNodeWithText("Sort all").performClick()
        compose.onNodeWithText("Jupiter").performClick()
        compose.onNodeWithText("Jupiter").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Add").performClick()

        assertThat(calls).containsExactly(
            "quicksort:2",
            "merge:2",
            "keepBoth:2",
            "quicksort:null",
            "edit:1",
            "select:1",
            "add",
        ).inOrder()
    }

    @Test
    fun `selection mode swaps the app bar for bulk actions`() {
        val a = tx(1, "Swiggy", TransactionStatus.CATEGORIZED, category = food.id)
        show(
            TransactionsUiState(
                loading = false,
                days = listOf(DayGroup(LocalDate.of(2026, 1, 1), listOf(a), 450_00, 0)),
                categories = mapOf(food.id to food),
                selection = setOf(1),
                total = 1,
            ),
        )
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithContentDescription("Categorize").performClick()
        compose.onNodeWithContentDescription("Ignore").performClick()
        compose.onNodeWithText("Swiggy").performClick()
        compose.onNodeWithContentDescription("Clear selection").performClick()
        assertThat(calls).containsExactly("categorize:[1]:true", "ignore:[1]", "select:1", "clearSelection").inOrder()
    }

    @Test
    fun `empty states tell nothing-yet from nothing-matches`() {
        show(TransactionsUiState(loading = false, total = 0))
        compose.onNodeWithText("No transactions yet").assertExists()
    }

    @Test
    fun `no match offers to clear filters`() {
        show(TransactionsUiState(loading = false, total = 3))
        compose.onNodeWithText("Nothing matches").assertExists()
    }
}
