package io.github.swashbuckler104.budgetwidget.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxListener
import io.github.swashbuckler104.budgetwidget.core.data.sort.AutoSortSetting
import io.github.swashbuckler104.budgetwidget.core.data.sort.SettingsAutoSort
import io.github.swashbuckler104.budgetwidget.core.data.startup.ProcessStartTask

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun autoSort(impl: SettingsAutoSort): AutoSortSetting

    /** Declared so the sets exist (empty) even in builds where no module contributes. */
    @Multibinds
    abstract fun inboxListeners(): Set<InboxListener>

    @Multibinds
    abstract fun processStartTasks(): Set<ProcessStartTask>

    companion object {
        @Provides
        fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }
    }
}
