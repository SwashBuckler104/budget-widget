package io.github.swashbuckler104.budgetwidget.feature.analysis

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.analysis.AnalysisPeriod
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AnalysisViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: BudgetDatabase
    private val viewModels = mutableListOf<AnalysisViewModel>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 10)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                BudgetDatabase::class.java,
            )
                .build()
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        storeScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()): AnalysisViewModel {
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = storeScope) { File(tmp.root, "s.preferences_pb") },
        )
        return AnalysisViewModel(
            TransactionRepository(db.transactionDao(), db.unparsedSmsDao(), db.accountDao()),
            CategoryRepository(db),
            DayCloseRepository(db, settings) { now },
            { now },
            saved,
        ).also { viewModels += it }
    }

    private suspend fun AnalysisViewModel.await(predicate: (AnalysisUiState) -> Boolean) =
        withTimeout(TIMEOUT_MS) { state.first { !it.loading && predicate(it) } }

    @Test
    fun `shows the current month and moves only between the first and current month`() = runBlocking {
        val fuel = db.categoryDao().insert(
            CategoryEntity(
                parentId = null,
                name = "Fuel",
                iconKey = "⛽",
                colorArgb = 0,
                kind = CategoryKind.EXPENSE,
                sortOrder = 0,
            ),
        )
        txn("nov", today.atTime(9, 0), 500_00, fuel)
        txn("sep", LocalDate.of(2026, 9, 3).atTime(9, 0), 200_00, fuel)
        val vm = viewModel()

        val first = vm.await { it.analysis?.spentPaise == 500_00L }
        assertThat(first.period).isEqualTo(AnalysisPeriod.Month(YearMonth.of(2026, 11)))
        assertThat(first.firstDate).isEqualTo(LocalDate.of(2026, 9, 3))
        assertThat(first.canGoForward).isFalse()
        assertThat(first.canGoBack).isTrue()

        vm.shift(1)
        assertThat(vm.await { true }.period).isEqualTo(AnalysisPeriod.Month(YearMonth.of(2026, 11)))
        vm.shift(-1)
        vm.shift(-1)
        val sep = vm.await { it.period == AnalysisPeriod.Month(YearMonth.of(2026, 9)) }
        assertThat(sep.analysis!!.spentPaise).isEqualTo(200_00)
        assertThat(sep.canGoBack).isFalse()
        vm.shift(-1)
        assertThat(vm.await { true }.period).isEqualTo(AnalysisPeriod.Month(YearMonth.of(2026, 9)))

        val (from, to) = vm.periodRange()
        assertThat(from).isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli())
        assertThat(to).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    @Test
    fun `switches between week, year and a custom range and keeps the choice in saved state`() = runBlocking {
        txn("a", today.atTime(9, 0), 100_00, null)
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        vm.await { true }
        vm.selectKind(PeriodKind.WEEK)
        // 2026-11-10 is a Tuesday.
        assertThat(vm.await { it.period is AnalysisPeriod.Week }.period.start).isEqualTo(LocalDate.of(2026, 11, 9))
        vm.selectKind(PeriodKind.YEAR)
        assertThat(vm.await { it.period is AnalysisPeriod.Year }.period).isEqualTo(AnalysisPeriod.Year(2026))
        // Reversed and future ends are fixed up.
        vm.selectRange(LocalDate.of(2026, 12, 25), LocalDate.of(2026, 10, 1))
        val range = vm.await { it.period is AnalysisPeriod.Custom }
        assertThat(range.period).isEqualTo(AnalysisPeriod.Custom(LocalDate.of(2026, 10, 1), today))
        assertThat(range.canGoBack).isFalse()

        // A new view model over the same saved state (process death) shows the same range.
        val restored = viewModel(saved).await { true }
        assertThat(restored.period).isEqualTo(range.period)
    }

    @Test
    fun `counts the inbox for the banner`() = runBlocking {
        txn("a", today.atTime(9, 0), 100_00, null)
        assertThat(viewModel().await { it.pending == 1 }.analysis!!.categories.single().category).isNull()
    }

    private suspend fun txn(hash: String, at: LocalDateTime, amount: Long, category: Long?) {
        val millis = at.atZone(zone).toInstant().toEpochMilli()
        db.transactionDao().insertOrIgnore(
            TransactionEntity(
                txnHash = hash,
                source = TransactionSource.SMS,
                amountPaise = amount,
                direction = Direction.OUT,
                parserType = null,
                merchantRaw = "Shop",
                merchantDisplay = "Shop",
                merchantKey = "shop",
                accountId = null,
                occurredAt = millis,
                smsSender = null,
                smsBody = null,
                reference = null,
                balanceAfterPaise = null,
                isCard = false,
                status = if (category == null) TransactionStatus.PENDING else TransactionStatus.CATEGORIZED,
                categoryId = category,
                isBackfill = false,
                createdAt = millis,
                updatedAt = millis,
            ),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
