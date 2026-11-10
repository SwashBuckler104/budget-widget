package io.github.swashbuckler104.budgetwidget.notifications.day

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import io.github.swashbuckler104.budgetwidget.core.data.inbox.QuickSortLauncher
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.notifications.NotificationChannels
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class DayTicksTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val zone = ZoneId.of("Asia/Kolkata")
    private lateinit var defaultZone: TimeZone
    private lateinit var db: BudgetDatabase
    private lateinit var settings: SettingsStore
    private lateinit var handler: DayTickHandler
    private lateinit var scheduler: DayTickScheduler
    private lateinit var nudge: NudgeNotifier
    private var now = at(10, 21)
    private var refreshes = 0

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val launcher = object : QuickSortLauncher {
        override fun transaction(context: Context, transactionId: Long) = Intent("sort")

        override fun inbox(context: Context) = Intent("inbox")
    }

    @Before
    fun setUp() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.ensure(context)
        db = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        TimeZone.setDefault(defaultZone)
    }

    private fun TestScope.wire() {
        settings =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    File(tmp.root, "s.preferences_pb")
                },
            )
        val clock = { now }
        val days = DayCloseRepository(db, settings, clock)
        scheduler = DayTickScheduler(context, settings, clock)
        nudge = NudgeNotifier(context, days, launcher)
        val widgets = object : InboxWidgets {
            override suspend fun refreshNow() {
                refreshes++
            }
        }
        handler = DayTickHandler(scheduler, days, widgets, nudge, settings, clock)
    }

    private suspend fun pending(hash: String, backfill: Boolean = false) = db.transactionDao().insertOrIgnore(
        TransactionEntity(
            txnHash = hash,
            source = TransactionSource.SMS,
            amountPaise = 100_00,
            direction = Direction.OUT,
            parserType = null,
            merchantRaw = null,
            merchantDisplay = null,
            merchantKey = null,
            accountId = null,
            occurredAt = now,
            smsSender = null,
            smsBody = null,
            reference = null,
            balanceAfterPaise = null,
            isCard = false,
            status = TransactionStatus.PENDING,
            isBackfill = backfill,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private data class Alarm(val action: String?, val atMillis: Long, val type: Int)

    // Robolectric exposes the alarm's PendingIntent only as a deprecated field (no getter exists).
    @Suppress("DEPRECATION")
    private fun scheduled(): List<Alarm> =
        shadowOf(alarms).scheduledAlarms.map { alarm: ShadowAlarmManager.ScheduledAlarm ->
            Alarm(shadowOf(alarm.operation!!).savedIntent.action, alarm.triggerAtMs, alarm.getType())
        }

    private fun nudgeShown(): Notification? = notifications.activeNotifications.firstOrNull {
        it.id ==
            NudgeNotifier.NUDGE_ID
    }?.notification

    @Test
    fun `the nudge counts open transactions, marks the day and re-arms for tomorrow`() = runTest {
        wire()
        pending("a")
        pending("b")
        pending("old", backfill = true)

        handler.handle(DayTickHandler.Kind.NUDGE)

        val shown = nudgeShown()!!
        assertThat(shown.channelId).isEqualTo(NotificationChannels.REMINDERS)
        assertThat(shown.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("2 transactions left to sort today")
        assertThat(shown.actions.single().title.toString()).isEqualTo("Sort now")
        assertThat(settings.lastNudgeEpochDay()).isEqualTo(LocalDate.of(2026, 11, 10).toEpochDay())
        assertThat(refreshes).isEqualTo(1)

        val all = scheduled()
        assertThat(all).containsExactly(
            Alarm(DayTickReceiver.ACTION_NUDGE, at(11, 21), AlarmManager.RTC_WAKEUP),
            Alarm(DayTickReceiver.ACTION_TICK, at(10, 22), AlarmManager.RTC),
        )
    }

    @Test
    fun `no nudge when the inbox is clear, and sorting the last one removes it`() = runTest {
        wire()
        handler.handle(DayTickHandler.Kind.NUDGE)
        assertThat(nudgeShown()).isNull()

        val a = pending("a")
        nudge.nudge()
        assertThat(nudgeShown()).isNotNull()
        db.transactionDao().ignore(a, IgnoreReason.USER, now)
        nudge.onSorted(a)
        assertThat(nudgeShown()).isNull()
    }

    @Test
    fun `turning the nudge off cancels its alarm and a tick never nudges`() = runTest {
        wire()
        pending("a")
        settings.setNudgeEnabled(false)
        scheduler.arm()
        assertThat(scheduled().map { it.action }).containsExactly(DayTickReceiver.ACTION_TICK)
        handler.handle(DayTickHandler.Kind.NUDGE)
        handler.handle(DayTickHandler.Kind.TICK)
        assertThat(nudgeShown()).isNull()
        assertThat(settings.nudge.first().enabled).isFalse()
    }

    @Test
    fun `the midnight tick records the finished day`() = runTest {
        wire()
        settings.setDayTrackingSince(LocalDate.of(2026, 11, 9).toEpochDay())
        now = at(11, 0, 5)
        handler.handle(DayTickHandler.Kind.TICK)
        assertThat(db.dayCloseDao().recent(5).map { it.date }).containsExactly("2026-11-10", "2026-11-09").inOrder()
    }
}
