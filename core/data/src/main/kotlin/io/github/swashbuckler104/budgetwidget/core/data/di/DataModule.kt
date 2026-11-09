package io.github.swashbuckler104.budgetwidget.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.swashbuckler104.budgetwidget.core.data.WallClock

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    @Provides
    fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }
}
