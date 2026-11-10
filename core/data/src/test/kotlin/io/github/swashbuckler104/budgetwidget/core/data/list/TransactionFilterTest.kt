package io.github.swashbuckler104.budgetwidget.core.data.list

import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

class TransactionFilterTest {
    private fun tx(
        id: Long = 1,
        amount: Long = 450_00,
        direction: Direction = Direction.OUT,
        status: TransactionStatus = TransactionStatus.CATEGORIZED,
        category: Long? = GROCERIES,
        merchant: String? = "Blinkit",
        note: String? = null,
        at: Long = 0,
        auto: Boolean = false,
        account: Long? = 3,
    ) = Transaction(
        id = id, source = TransactionSource.SMS, amountPaise = amount, direction = direction, parserType = null,
        merchantDisplay = merchant, bankName = "Axis Bank", accountLast4 = "2207", occurredAt = at, isCard = false,
        status = status, isBackfill = false, duplicateOfId = null, categoryId = category, autoCategorized = auto,
        note = note, accountId = account,
    )

    private val parents = mapOf(GROCERIES to FOOD, FOOD to null, SALARY to null, SAVINGS to null)
    private val names = mapOf(GROCERIES to "Groceries", FOOD to "Food & Drinks", SALARY to "Salary")
    private fun TransactionFilter.test(t: Transaction) = matches(t, { parents[it] }, { names[it] })

    @Test
    fun `the default filter matches everything and is inactive`() {
        assertThat(TransactionFilter().isActive).isFalse()
        assertThat(TransactionFilter().test(tx(status = TransactionStatus.IGNORED, category = null))).isTrue()
    }

    @Test
    fun `search covers merchant, note, category, bank and amount prefixes`() {
        assertThat(TransactionFilter(query = "blink").test(tx())).isTrue()
        assertThat(TransactionFilter(query = "dinner").test(tx(note = "Dinner with A"))).isTrue()
        assertThat(TransactionFilter(query = "grocer").test(tx())).isTrue()
        assertThat(TransactionFilter(query = "axis").test(tx())).isTrue()
        assertThat(TransactionFilter(query = "45").test(tx())).isTrue()
        assertThat(TransactionFilter(query = "₹4,500").test(tx(amount = 4_500_00))).isTrue()
        assertThat(TransactionFilter(query = "1200.5").test(tx(amount = 1_200_50))).isTrue()
        assertThat(TransactionFilter(query = "46").test(tx())).isFalse()
        assertThat(TransactionFilter(query = "1.2.3").test(tx())).isFalse()
        assertThat(TransactionFilter(query = "zomato").test(tx())).isFalse()
    }

    @Test
    fun `a top-level category matches its subcategories`() {
        assertThat(TransactionFilter(categoryId = FOOD).test(tx())).isTrue()
        assertThat(TransactionFilter(categoryId = GROCERIES).test(tx())).isTrue()
        assertThat(TransactionFilter(categoryId = SALARY).test(tx())).isFalse()
        assertThat(TransactionFilter(categoryId = FOOD).test(tx(category = null))).isFalse()
    }

    @Test
    fun `status, direction, account and date range`() {
        val auto = tx(auto = true)
        assertThat(TransactionFilter(status = StatusFilter.AUTO_SORTED).test(auto)).isTrue()
        assertThat(TransactionFilter(status = StatusFilter.AUTO_SORTED).test(tx())).isFalse()
        assertThat(TransactionFilter(status = StatusFilter.PENDING).test(tx(status = TransactionStatus.PENDING)))
            .isTrue()
        assertThat(TransactionFilter(direction = Direction.IN).test(tx())).isFalse()
        assertThat(TransactionFilter(accountId = 4).test(tx())).isFalse()
        assertThat(TransactionFilter(fromMillis = 10, toMillis = 20).test(tx(at = 10))).isTrue()
        assertThat(TransactionFilter(fromMillis = 10, toMillis = 20).test(tx(at = 20))).isFalse()
    }

    @Test
    fun `day groups keep order and total spending and income without transfers or ignored`() {
        val day = 86_400_000L
        val kinds = mapOf(
            GROCERIES to CategoryKind.EXPENSE,
            SALARY to CategoryKind.INCOME,
            SAVINGS to CategoryKind.TRANSFER,
        )
        val groups = groupByDay(
            listOf(
                tx(id = 1, at = day + 5, amount = 100_00),
                tx(id = 2, at = day + 4, amount = 50_00, status = TransactionStatus.PENDING, category = null),
                tx(id = 3, at = day + 3, amount = 999_00, status = TransactionStatus.IGNORED),
                tx(id = 4, at = day + 2, amount = 700_00, category = SAVINGS),
                tx(id = 5, at = day + 1, amount = 1_000_00, direction = Direction.IN, category = SALARY),
                tx(id = 6, at = 7, amount = 10_00),
            ),
            ZoneOffset.UTC,
        ) { kinds[it] }

        assertThat(groups.map { it.date }).containsExactly(LocalDate.of(1970, 1, 2), LocalDate.of(1970, 1, 1)).inOrder()
        assertThat(groups[0].transactions.map { it.id }).containsExactly(1L, 2L, 3L, 4L, 5L).inOrder()
        assertThat(groups[0].spentPaise).isEqualTo(150_00)
        assertThat(groups[0].incomePaise).isEqualTo(1_000_00)
        assertThat(groups[1].spentPaise).isEqualTo(10_00)
    }

    private companion object {
        const val FOOD = 10L
        const val GROCERIES = 11L
        const val SALARY = 20L
        const val SAVINGS = 30L
    }
}
