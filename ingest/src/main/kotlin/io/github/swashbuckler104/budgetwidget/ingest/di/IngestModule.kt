package io.github.swashbuckler104.budgetwidget.ingest.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import io.github.swashbuckler104.budgetwidget.ingest.parser.PennyWiseSmsParser
import io.github.swashbuckler104.budgetwidget.ingest.work.ContentResolverSmsInbox
import io.github.swashbuckler104.budgetwidget.ingest.work.IngestWorkScheduler
import io.github.swashbuckler104.budgetwidget.ingest.work.SmsInboxSource

@Module
@InstallIn(SingletonComponent::class)
internal abstract class IngestModule {
    @Binds
    abstract fun smsParser(impl: PennyWiseSmsParser): SmsParser

    @Binds
    abstract fun smsSync(impl: IngestWorkScheduler): SmsSync

    @Binds
    abstract fun smsInbox(impl: ContentResolverSmsInbox): SmsInboxSource

    companion object {
        /** Requires the Application to implement `Configuration.Provider` (on-demand init). */
        @Provides
        fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
    }
}
