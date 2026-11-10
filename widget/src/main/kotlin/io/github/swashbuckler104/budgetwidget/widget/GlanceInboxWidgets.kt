package io.github.swashbuckler104.budgetwidget.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws every Quick-Sort widget instance. A no-op when none is on a home screen. */
@Singleton
internal class GlanceInboxWidgets @Inject constructor(@param:ApplicationContext private val context: Context) :
    InboxWidgets {
    override suspend fun refreshNow() {
        QuickSortWidget().updateAll(context)
    }
}
