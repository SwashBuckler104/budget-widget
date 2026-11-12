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
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class PeriodAnalysisTest {
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
        val a = PeriodAnalysis.compute(txns, categories, AnalysisPeriod.Month(nov), today = d(10), zone = zone)

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
        val a = PeriodAnalysis.compute(txns, categories, AnalysisPeriod.Month(nov), today = d(30), zone = zone)

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
        val a = PeriodAnalysis.compute(txns, categories, AnalysisPeriod.Month(nov), today = d(3), zone = zone)

        assertThat(a.buckets).hasSize(30)
        assertThat(a.buckets.take(3)).containsExactly(150_00L, 0L, 30_00L).inOrder()
        assertThat(a.cumulative).containsExactly(150_00L, 150_00L, 180_00L).inOrder()
        assertThat(a.previousCumulative).hasSize(31)
        assertThat(a.previousCumulative.last()).isEqualTo(70_00)
        assertThat(a.topMerchants.first()).isEqualTo(MerchantSpend("HP", 150_00, 2, "hp"))
    }

    @Test
    fun `a past month compares with the whole previous month and an account filter narrows`() {
        val txns = listOf(
            tx(d(5, month = 10), 100_00, fuel, account = 1),
            tx(d(6, month = 10), 900_00, fuel, account = 2),
            tx(d(25, month = 9), 200_00, fuel, account = 1),
        )
        val oct = PeriodAnalysis.compute(
            txns,
            categories,
            AnalysisPeriod.Month(YearMonth.of(2026, 10)),
            today = d(10),
            zone = zone,
            accountId = 1,
        )
        assertThat(oct.spentPaise).isEqualTo(100_00)
        assertThat(oct.daysCounted).isEqualTo(31)
        assertThat(oct.previousSpentPaise).isEqualTo(200_00)
        assertThat(oct.cumulative).hasSize(31)
        assertThat(oct.changePercent).isEqualTo(-50)

        val future = PeriodAnalysis.compute(
            txns,
            categories,
            AnalysisPeriod.Month(YearMonth.of(2026, 12)),
            today = d(10),
            zone = zone,
        )
        assertThat(future.daysCounted).isEqualTo(0)
        assertThat(future.averagePerDayPaise).isEqualTo(0)
    }

    @Test
    fun `months are local months and a tiny previous month doesn't overflow the percentage`() {
        val ist = ZoneId.of("Asia/Kolkata")
        // 31 Oct 23:30 IST is 18:00 UTC: October locally, even though the UTC date is also the 31st;
        // 1 Nov 00:15 IST is 31 Oct in UTC but November locally.
        val octLate = Transaction(
            id = 90, source = TransactionSource.SMS, amountPaise = 1, direction = Direction.OUT, parserType = null,
            merchantDisplay = null, bankName = null, accountLast4 = null,
            occurredAt = LocalDate.of(2026, 10, 31).atTime(23, 30).atZone(ist).toInstant().toEpochMilli(),
            isCard = false, status = TransactionStatus.CATEGORIZED, isBackfill = false, duplicateOfId = null,
            categoryId = fuel.id,
        )
        val novEarly = octLate.copy(
            id = 91,
            amountPaise = 50_000_000_00,
            occurredAt = LocalDate.of(2026, 11, 1).atTime(0, 15).atZone(ist).toInstant().toEpochMilli(),
        )
        // November is a past month here, so it's compared with all of October.
        val a = PeriodAnalysis.compute(
            listOf(octLate, novEarly),
            categories,
            AnalysisPeriod.Month(nov),
            today = d(5, month = 12),
            zone = ist,
        )
        assertThat(a.spentPaise).isEqualTo(50_000_000_00)
        assertThat(a.previousSpentPaise).isEqualTo(1)
        assertThat(a.buckets.first()).isEqualTo(50_000_000_00)
        assertThat(a.changePercent).isEqualTo(999_999)
    }

    @Test
    fun `last days spans month boundaries`() {
        val txns = listOf(tx(d(30, month = 10), 10_00, fuel), tx(d(2), 20_00), tx(d(3), 5_00, savings))
        assertThat(PeriodAnalysis.lastDays(txns, categories, today = d(3), zone = zone, days = 7))
            .containsExactly(0L, 0L, 10_00L, 0L, 0L, 20_00L, 0L).inOrder()
    }

    @Test
    fun `a week compares with last week up to the same weekday and marks today`() {
        // 2026-11-11 is a Wednesday; its week runs Mon 9 to Sun 15.
        val txns = listOf(
            tx(d(9), 100_00, fuel),
            tx(d(11), 50_00, fuel),
            tx(d(14), 999_00, fuel),
            tx(d(3), 40_00, fuel),
            tx(d(6), 70_00, fuel),
        )
        val week = AnalysisPeriod.Week.of(d(11))
        assertThat(week.start).isEqualTo(d(9))
        val a = PeriodAnalysis.compute(txns, categories, week, today = d(11), zone = zone)
        assertThat(a.buckets).containsExactly(100_00L, 0L, 50_00L, 0L, 0L, 999_00L, 0L).inOrder()
        assertThat(a.todayBucket).isEqualTo(2)
        assertThat(a.daysCounted).isEqualTo(3)
        // Last week up to Wednesday the 4th: only the 3rd counts, not Friday the 6th.
        assertThat(a.previousSpentPaise).isEqualTo(40_00)
        assertThat(a.previousCumulative).hasSize(7)
        assertThat(a.cumulative).containsExactly(100_00L, 100_00L, 150_00L).inOrder()
    }

    @Test
    fun `a year is charted by month and a long custom range too`() {
        val txns = listOf(
            tx(d(5, month = 1), 10_00, fuel),
            tx(d(9), 20_00, fuel),
            tx(LocalDate.of(2025, 6, 1), 30_00, fuel),
        )
        val year = PeriodAnalysis.compute(txns, categories, AnalysisPeriod.Year(2026), today = d(11), zone = zone)
        assertThat(year.buckets).hasSize(12)
        assertThat(year.buckets.first()).isEqualTo(10_00)
        assertThat(year.buckets[10]).isEqualTo(20_00)
        assertThat(year.todayBucket).isEqualTo(10)
        assertThat(year.cumulative).hasSize(11)
        assertThat(year.averagePerBucketPaise).isEqualTo(30_00 / 11)
        assertThat(year.previousSpentPaise).isEqualTo(30_00)

        val long = AnalysisPeriod.Custom(d(20, month = 7), d(11))
        assertThat(long.unit).isEqualTo(BucketUnit.MONTH)
        assertThat(PeriodAnalysis.bucketCount(long)).isEqualTo(5)
        assertThat(PeriodAnalysis.bucketStart(long, 0)).isEqualTo(d(20, month = 7))
        assertThat(PeriodAnalysis.bucketStart(long, 1)).isEqualTo(d(1, month = 8))
        assertThat(AnalysisPeriod.Custom(d(20, month = 9), d(11)).unit).isEqualTo(BucketUnit.DAY)
        val short = AnalysisPeriod.Custom(d(1), d(10))
        assertThat(short.unit).isEqualTo(BucketUnit.DAY)
        assertThat(short.previous()).isEqualTo(AnalysisPeriod.Custom(d(22, month = 10), d(31, month = 10)))
        assertThat(short.shift(1)).isNull()
    }

    @Test
    fun `income has its own category breakdown`() {
        val txns = listOf(
            tx(d(1), 50_000_00, salary, direction = Direction.IN),
            tx(d(2), 2_000_00, direction = Direction.IN),
            tx(d(3), 100_00, fuel),
            tx(d(4), 500_00, savings, direction = Direction.IN),
        )
        val a = PeriodAnalysis.compute(txns, categories, AnalysisPeriod.Month(nov), today = d(30), zone = zone)
        assertThat(a.incomePaise).isEqualTo(52_000_00)
        assertThat(a.incomeCategories.map { it.category?.name }).containsExactly("Salary", null).inOrder()
        assertThat(a.categories.map { it.category?.name }).containsExactly("Fuel")
    }

    @Test
    fun `periods survive an encode and decode round trip`() {
        listOf(
            AnalysisPeriod.Week.of(d(11)),
            AnalysisPeriod.Month(nov),
            AnalysisPeriod.Year(2026),
            AnalysisPeriod.Custom(d(1, month = 1), d(11)),
        ).forEach { assertThat(AnalysisPeriod.decode(it.encode())).isEqualTo(it) }
        assertThat(AnalysisPeriod.decode("C:2026-11-10:2026-11-01")).isNull()
        assertThat(AnalysisPeriod.decode("junk")).isNull()
        assertThat(AnalysisPeriod.decode("W:2026-11-11")).isNull()
    }

    @Test
    fun `the same-day comparison uses calendar dates across leap years`() {
        // 2028 is a leap year: on 1 Mar 2028 the comparison runs to 1 Mar 2027, not 2 Mar.
        val txns = listOf(tx(LocalDate.of(2027, 3, 1), 10_00, fuel), tx(LocalDate.of(2027, 3, 2), 99_00, fuel))
        val y2028 = PeriodAnalysis.compute(
            txns,
            categories,
            AnalysisPeriod.Year(2028),
            today = LocalDate.of(2028, 3, 1),
            zone = zone,
        )
        assertThat(y2028.previousSpentPaise).isEqualTo(10_00)
        // On 1 Mar 2029 the comparison includes 29 Feb 2028.
        val leap = listOf(tx(LocalDate.of(2028, 2, 29), 7_00, fuel), tx(LocalDate.of(2028, 3, 2), 99_00, fuel))
        val y2029 = PeriodAnalysis.compute(
            leap,
            categories,
            AnalysisPeriod.Year(2029),
            today = LocalDate.of(2029, 3, 1),
            zone = zone,
        )
        assertThat(y2029.previousSpentPaise).isEqualTo(7_00)
    }

    @Test
    fun `a monthly custom range lays the previous range onto its own buckets`() {
        // 15 Jan - 30 Apr 2026 (106 days, 4 monthly bars); the range before is 1 Oct 2025 - 14 Jan 2026.
        val range = AnalysisPeriod.Custom(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 4, 30))
        val txns = listOf(tx(LocalDate.of(2025, 10, 1), 5_00, fuel), tx(LocalDate.of(2026, 1, 14), 3_00, fuel))
        val a = PeriodAnalysis.compute(txns, categories, range, today = LocalDate.of(2026, 6, 1), zone = zone)
        assertThat(a.buckets).hasSize(4)
        assertThat(a.previousCumulative).hasSize(4)
        // Day 0 of the previous range maps to 15 Jan (bucket 0); its last day maps to 30 Apr (bucket 3).
        assertThat(a.previousCumulative).containsExactly(5_00L, 5_00L, 5_00L, 8_00L).inOrder()
        assertThat(a.previousSpentPaise).isEqualTo(8_00)
    }
}
