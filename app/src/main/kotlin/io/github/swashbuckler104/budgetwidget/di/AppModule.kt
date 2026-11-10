package io.github.swashbuckler104.budgetwidget.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.swashbuckler104.budgetwidget.AppQuickSortLauncher
import io.github.swashbuckler104.budgetwidget.core.data.inbox.QuickSortLauncher

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppModule {
    @Binds
    abstract fun quickSortLauncher(impl: AppQuickSortLauncher): QuickSortLauncher
}
