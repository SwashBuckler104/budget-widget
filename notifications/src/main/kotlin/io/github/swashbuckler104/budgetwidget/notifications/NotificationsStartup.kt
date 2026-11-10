package io.github.swashbuckler104.budgetwidget.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.swashbuckler104.budgetwidget.core.data.startup.ProcessStartTask
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Creates the channels and keeps posted notifications consistent with settings and the inbox. */
internal class NotificationsStartup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val notifier: TransactionNotifier,
) : ProcessStartTask {
    override fun start(scope: CoroutineScope) {
        NotificationChannels.ensure(context)
        scope.launch { notifier.pruneStale() }
        scope.launch {
            // Turning notifications off clears the ones already up.
            settings.notifications.map { it.enabled }.distinctUntilChanged().filter { !it }
                .collect { notifier.cancelAll() }
        }
    }
}
