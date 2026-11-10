package io.github.swashbuckler104.budgetwidget.feature.transactions.edit

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.edit.ManualTransaction
import io.github.swashbuckler104.budgetwidget.core.data.edit.TransactionEdit
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionDetail
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditTransactionContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val groceries =
        Category(5, null, "Groceries", "🛒", 0, CategoryKind.EXPENSE, 0, archived = false, isSeed = true)
    private val salary = Category(6, null, "Salary", "💼", 0, CategoryKind.INCOME, 1, archived = false, isSeed = true)
    private val groups = listOf(CategoryGroup(groceries, emptyList()), CategoryGroup(salary, emptyList()))
    private val categories = mapOf(groceries.id to groceries, salary.id to salary)

    private fun detail(source: TransactionSource, body: String? = "Rs 450 debited") = TransactionDetail(
        transaction = Transaction(
            id = 9, source = source, amountPaise = 450_00, direction = Direction.OUT, parserType = null,
            merchantDisplay = "Blinkit", bankName = "Axis Bank", accountLast4 = "2207", occurredAt = 0, isCard = false,
            status = TransactionStatus.CATEGORIZED, isBackfill = false, duplicateOfId = null, merchantKey = "blinkit",
            categoryId = groceries.id,
        ),
        smsSender = "AD-AXISBK",
        smsBody = body,
        reference = null,
        balanceAfterPaise = null,
    )

    private val saved = mutableListOf<TransactionEdit>()
    private var reported = false

    private fun show(detail: TransactionDetail) = compose.setContent {
        EditTransactionContent(
            detail,
            categories,
            groups,
            EditActions(
                onSave = { saved += it },
                onIgnore = {},
                onUnignore = {},
                onDelete = {},
                onReportWrongAmount = { reported = true },
                onDismiss = {},
            ),
        )
    }

    @Test
    fun `switching a sorted expense to income asks for an income category`() {
        show(detail(TransactionSource.SMS))
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Income").performClick()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Choose a category of this type").assertExists()

        compose.onNode(hasText("Salary") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertThat(saved.single().direction).isEqualTo(Direction.IN)
        assertThat(saved.single().categoryId).isEqualTo(salary.id)
        assertThat(saved.single().amountPaise).isNull()
    }

    @Test
    fun `the original SMS can be shown and reported`() {
        show(detail(TransactionSource.SMS))
        compose.onNodeWithText("Original SMS").performScrollTo().performClick()
        compose.onNodeWithText("Rs 450 debited").assertExists()
        compose.onNodeWithText("Report wrong amount").performScrollTo().performClick()
        assertThat(reported).isTrue()
    }

    @Test
    fun `manual entries edit the amount`() {
        show(detail(TransactionSource.MANUAL, body = null))
        compose.onNodeWithText("450").performTextReplacement("1,250.5")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertThat(saved.single().amountPaise).isEqualTo(1_250_50)
        compose.onNodeWithText("Delete").assertExists()
    }

    @Test
    fun `adding needs an amount and a category`() {
        val added = mutableListOf<ManualTransaction>()
        compose.setContent { AddTransactionContent(groups, categories, nowMillis = 1_000, onAdd = { added += it }) }
        compose.onNodeWithText("Add").performScrollTo().performClick()
        assertThat(added).isEmpty()
        compose.onNodeWithText("Enter an amount above ₹0 with at most 2 decimals").assertExists()

        compose.onNodeWithText("Amount").performTextReplacement("99")
        compose.onNode(hasText("Groceries") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Add").performScrollTo().performClick()
        assertThat(added.single()).isEqualTo(ManualTransaction(99_00, Direction.OUT, groceries.id, "", "", 1_000))
    }
}
