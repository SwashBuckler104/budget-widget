package io.github.swashbuckler104.budgetwidget.notifications.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxListener
import io.github.swashbuckler104.budgetwidget.core.data.startup.ProcessStartTask
import io.github.swashbuckler104.budgetwidget.notifications.NotificationsStartup
import io.github.swashbuckler104.budgetwidget.notifications.TransactionNotifier
import io.github.swashbuckler104.budgetwidget.notifications.day.DayTicksStartup
import io.github.swashbuckler104.budgetwidget.notifications.day.NudgeNotifier

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {
    @Binds
    @IntoSet
    abstract fun transactionNotifier(impl: TransactionNotifier): InboxListener

    @Binds
    @IntoSet
    abstract fun nudgeNotifier(impl: NudgeNotifier): InboxListener

    @Binds
    @IntoSet
    abstract fun startup(impl: NotificationsStartup): ProcessStartTask

    @Binds
    @IntoSet
    abstract fun dayTicks(impl: DayTicksStartup): ProcessStartTask
}
