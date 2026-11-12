package io.github.swashbuckler104.budgetwidget.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives notification button taps. The app process may be cold: Hilt is reached through an
 * entry point and the database work runs under [goAsync], well within the broadcast time limit.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Entry {
        fun notificationActionHandler(): NotificationActionHandler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val command = NotificationCommand.fromIntent(intent) ?: return
        val handler = EntryPointAccessors.fromApplication(context, Entry::class.java).notificationActionHandler()
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(TIMEOUT) { handler.handle(command) }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** Broadcasts get about 10 s before Android considers the receiver stuck. */
        val TIMEOUT = 8.seconds
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
