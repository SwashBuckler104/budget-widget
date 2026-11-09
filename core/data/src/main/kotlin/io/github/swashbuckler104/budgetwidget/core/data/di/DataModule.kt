package io.github.swashbuckler104.budgetwidget.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.sort.AutoSortSetting
import io.github.swashbuckler104.budgetwidget.core.data.sort.SettingsAutoSort

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun autoSort(impl: SettingsAutoSort): AutoSortSetting

    companion object {
        @Provides
        fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }
    }
}
