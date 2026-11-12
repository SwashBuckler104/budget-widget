package io.github.swashbuckler104.budgetwidget.widget.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import io.github.swashbuckler104.budgetwidget.core.data.startup.ProcessStartTask
import io.github.swashbuckler104.budgetwidget.widget.GlanceInboxWidgets
import io.github.swashbuckler104.budgetwidget.widget.WidgetSync

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetModule {
    @Binds
    abstract fun inboxWidgets(impl: GlanceInboxWidgets): InboxWidgets

    @Binds
    @IntoSet
    abstract fun sync(impl: WidgetSync): ProcessStartTask
}
