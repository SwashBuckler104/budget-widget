package io.github.swashbuckler104.budgetwidget.feature.settings.di

import android.content.ContentResolver
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object SettingsModule {
    /** Opens the backup and export files the user picks (Storage Access Framework). */
    @Provides
    fun contentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver
}
