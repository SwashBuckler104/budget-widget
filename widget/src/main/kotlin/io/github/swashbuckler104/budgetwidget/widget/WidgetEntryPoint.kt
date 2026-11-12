package io.github.swashbuckler104.budgetwidget.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import io.github.swashbuckler104.budgetwidget.core.data.inbox.QuickSortLauncher
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase

/** Glance creates widgets and action callbacks itself, so they reach Hilt through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun loader(): WidgetSnapshotLoader

    fun insightLoader(): InsightSnapshotLoader

    fun launcher(): QuickSortLauncher

    fun sort(): SortTransactionUseCase

    fun widgets(): InboxWidgets

    companion object {
        fun get(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    }
}
