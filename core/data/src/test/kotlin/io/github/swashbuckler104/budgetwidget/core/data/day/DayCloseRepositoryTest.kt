package io.github.swashbuckler104.budgetwidget.core.data.day

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inMemoryDb
import io.github.swashbuckler104.budgetwidget.core.data.testTxn
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayCloseRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** India has no DST; the arithmetic must still use local, not UTC, midnights. */
    private val zone = ZoneId.of("Asia/Kolkata")
    private lateinit var db: BudgetDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: DayCloseRepository
    private var now = at(10, 12)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.wire() {
        settings =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    File(tmp.root, "s.preferences_pb")
                },
            )
        repo = DayCloseRepository(db, settings) { now }
    }

    private suspend fun stored(hash: String, createdAt: Long, backfill: Boolean = false, amount: Long = 100_00) =
        db.transactionDao().insertOrIgnore(
            testTxn(hash, amount = amount, at = createdAt).copy(createdAt = createdAt, isBackfill = backfill),
        )

    @Test
    fun `the first run only starts tracking`() = runTest {
        wire()
        assertThat(repo.closeFinishedDays(zone)).isEqualTo(0)
        assertThat(settings.dayTrackingSince()).isEqualTo(LocalDate.of(2026, 11, 10).toEpochDay())
        assertThat(db.dayCloseDao().latestDate()).isNull()
    }

    @Test
    fun `a day closes when nothing was waiting at its local midnight`() = runTest {
        wire()
        settings.setDayTrackingSince(LocalDate.of(2026, 11, 7).toEpochDay())
        // Nov 7: sorted before midnight -> closed. Nov 8: sorted after midnight -> open.
        val a = stored("a", at(7, 10))
        db.transactionDao().ignore(a, IgnoreReason.USER, now = at(7, 23, 59))
        val b = stored("b", at(8, 22))
        db.transactionDao().ignore(b, IgnoreReason.USER, now = at(9, 0, 1))
        // Nov 9: only backfill was waiting -> closed.
        stored("old", at(9, 9), backfill = true)

        assertThat(repo.closeFinishedDays(zone)).isEqualTo(3)
        val rows = db.dayCloseDao().recent(10).associate { it.date to it.closed }
        assertThat(rows).containsExactly("2026-11-09", true, "2026-11-08", false, "2026-11-07", true)
        assertThat(repo.closeFinishedDays(zone)).isEqualTo(0) // idempotent
    }

    @Test
    fun `status combines the open inbox, today's spending and the streak`() = runTest {
        wire()
        settings.setDayTrackingSince(LocalDate.of(2026, 11, 8).toEpochDay())
        val yesterday = stored("y", at(9, 9), amount = 999_00)
        db.transactionDao().ignore(yesterday, IgnoreReason.USER, now = at(9, 10))
        // Spent today, local time: 00:30 counts, 23:59 yesterday doesn't.
        val early = stored("t1", at(10, 0, 30), amount = 120_00)
        stored("late", at(9, 23, 59), amount = 50_00).let {
            db.transactionDao().ignore(it, IgnoreReason.USER, now = at(9, 23, 59))
        }

        with(repo.status(zone)) {
            assertThat(open).isEqualTo(1)
            assertThat(todaySpentPaise).isEqualTo(120_00)
            assertThat(streak).isEqualTo(2) // Nov 8 and 9 closed, today still open
        }
        db.transactionDao().ignore(early, IgnoreReason.USER, now = at(10, 13))
        assertThat(repo.status(zone).streak).isEqualTo(3)
    }

    @Test
    fun `a day recorded late still sees what was waiting at its midnight`() = runTest {
        wire()
        settings.setDayTrackingSince(LocalDate.of(2026, 11, 9).toEpochDay())
        val a = stored("a", at(9, 23))
        // Sorted the next morning, and the day only recorded after that.
        db.transactionDao().ignore(a, IgnoreReason.USER, now = at(10, 8))
        now = at(10, 9)
        repo.closeFinishedDays(zone)
        with(db.dayCloseDao().recent(1).single()) {
            assertThat(date).isEqualTo("2026-11-09")
            assertThat(pendingAtMidnight).isEqualTo(1)
            assertThat(closed).isFalse()
        }
    }
}
