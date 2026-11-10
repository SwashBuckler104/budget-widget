package io.github.swashbuckler104.budgetwidget.core.data.analysis

import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Test

class MonthAnalysisTest {
    private val zone = ZoneOffset.UTC
    private fun cat(id: Long, name: String, kind: CategoryKind, parent: Long? = null) =
        Category(id, parent, name, "🙂", 0, kind, 0, archived = false, isSeed = false)

    private val food = cat(1, "Food", CategoryKind.EXPENSE)
    private val groceries = cat(2, "Groceries", CategoryKind.EXPENSE, parent = 1)
    private val restaurant = cat(3, "Restaurant", CategoryKind.EXPENSE, parent = 1)
    private val fuel = cat(4, "Fuel", CategoryKind.EXPENSE)
    private val salary = cat(5, "Salary", CategoryKind.INCOME)
    private val savings = cat(6, "Savings", CategoryKind.TRANSFER)
    private val categories = listOf(food, groceries, restaurant, fuel, salary, savings).associateBy { it.id }

    private var nextId = 1L
    private fun tx(
        date: LocalDate,
        amount: Long,
        category: Category? = null,
        direction: Direction = Direction.OUT,
        status: TransactionStatus = if (category == null) TransactionStatus.PENDING else TransactionStatus.CATEGORIZED,
        merchant: String? = "Shop",
        account: Long? = 1,
    ) = Transaction(
        id = nextId++, source = TransactionSource.SMS, amountPaise = amount, direction = direction, parserType = null,
        merchantDisplay = merchant, bankName = null, accountLast4 = null,
        occurredAt = date.atTime(10, 0).toInstant(zone).toEpochMilli(), isCard = false, status = status,
        isBackfill = false, duplicateOfId = null, merchantKey = merchant?.lowercase(), categoryId = category?.id,
        accountId = account,
    )

    private val nov = YearMonth.of(2026, 11)
    private fun d(day: Int, month: Int = 11) = LocalDate.of(2026, month, day)

    @Test
    fun `totals, average and the comparison to the same day last month`() {
        val txns = listOf(
            tx(d(1), 100_00, groceries),
            tx(d(3), 300_00, restaurant),
            tx(d(5), 200_00),
            tx(d(5), 1_000_00, savings),
            tx(d(4), 999_00, fuel, status = TransactionStatus.IGNORED),
            tx(d(2), 5_000_00, salary, direction = Direction.IN),
            tx(d(5, month = 10), 400_00, fuel),
            tx(d(20, month = 10), 9_000_00, fuel),
        )
        val a = MonthAnalysis.compute(txns, categories, nov, today = d(10), zone = zone)

        assertThat(a.spentPaise).isEqualTo(600_00)
        assertThat(a.incomePaise).isEqualTo(5_000_00)
        assertThat(a.netPaise).isEqualTo(4_400_00)
        assertThat(a.daysCounted).isEqualTo(10)
        assertThat(a.averagePerDayPaise).isEqualTo(60_00)
        assertThat(a.previousSpentPaise).isEqualTo(400_00)
        assertThat(a.changePercent).isEqualTo(50)
        assertThat(a.transactionCount).isEqualTo(6)
    }

    @Test
    fun `categories group subcategories under their parent, with To sort for unsorted spending`() {
        val txns = listOf(
            tx(d(1), 100_00, groceries),
            tx(d(2), 300_00, restaurant),
            tx(d(3), 50_00, fuel),
            tx(d(4), 200_00),
        )
        val a = MonthAnalysis.compute(txns, categories, nov, today = d(30), zone = zone)

        assertThat(a.categories.map { it.category?.name }).containsExactly("Food", null, "Fuel").inOrder()
        val foodSpend = a.categories.first()
        assertThat(foodSpend.totalPaise).isEqualTo(400_00)
        assertThat(foodSpend.children.map { it.category!!.name }).containsExactly("Restaurant", "Groceries").inOrder()
        assertThat(a.categories.last().children).isEmpty()
    }

    @Test
    fun `daily bars, cumulative lines and top merchants`() {
        val txns = listOf(
            tx(d(1), 100_00, fuel, merchant = "HP"),
            tx(d(1), 50_00, fuel, merchant = "hp"),
            tx(d(3), 30_00, fuel, merchant = "Uber"),
            tx(d(30, month = 10), 70_00, fuel),
        )
        val a = MonthAnalysis.compute(txns, categories, nov, today = d(3), zone = zone)

        assertThat(a.daily).hasSize(30)
        assertThat(a.daily.take(3)).containsExactly(150_00L, 0L, 30_00L).inOrder()
        assertThat(a.cumulative).containsExactly(150_00L, 150_00L, 180_00L).inOrder()
        assertThat(a.previousCumulative).hasSize(31)
        assertThat(a.previousCumulative.last()).isEqualTo(70_00)
        assertThat(a.topMerchants.first()).isEqualTo(MerchantSpend("HP", 150_00, 2))
    }

    @Test
    fun `a past month compares with the whole previous month and an account filter narrows`() {
        val txns = listOf(
            tx(d(5, month = 10), 100_00, fuel, account = 1),
            tx(d(6, month = 10), 900_00, fuel, account = 2),
            tx(d(25, month = 9), 200_00, fuel, account = 1),
        )
        val oct = MonthAnalysis.compute(
            txns,
            categories,
            YearMonth.of(2026, 10),
            today = d(10),
            zone = zone,
            accountId = 1,
        )
        assertThat(oct.spentPaise).isEqualTo(100_00)
        assertThat(oct.daysCounted).isEqualTo(31)
        assertThat(oct.previousSpentPaise).isEqualTo(200_00)
        assertThat(oct.cumulative).hasSize(31)
        assertThat(oct.changePercent).isEqualTo(-50)

        val future = MonthAnalysis.compute(txns, categories, YearMonth.of(2026, 12), today = d(10), zone = zone)
        assertThat(future.daysCounted).isEqualTo(0)
        assertThat(future.averagePerDayPaise).isEqualTo(0)
    }

    @Test
    fun `last days spans month boundaries`() {
        val txns = listOf(tx(d(30, month = 10), 10_00, fuel), tx(d(2), 20_00), tx(d(3), 5_00, savings))
        assertThat(MonthAnalysis.lastDays(txns, categories, today = d(3), zone = zone, days = 7))
            .containsExactly(0L, 0L, 10_00L, 0L, 0L, 20_00L, 0L).inOrder()
    }
}
