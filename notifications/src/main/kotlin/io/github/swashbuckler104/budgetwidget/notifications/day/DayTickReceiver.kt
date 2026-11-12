package io.github.swashbuckler104.budgetwidget.notifications.day

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
 * Day alarms plus the system's clock and time-zone changes and our own app updates (all allowed
 * for manifest receivers on API 26+), which would otherwise leave alarms at the wrong wall time.
 */
class DayTickReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Entry {
        fun dayTickHandler(): DayTickHandler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val kind = when (intent.action) {
            ACTION_TICK -> DayTickHandler.Kind.TICK

            ACTION_NUDGE -> DayTickHandler.Kind.NUDGE

            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                DayTickHandler.Kind.CLOCK_CHANGED

            else -> return
        }
        val handler = EntryPointAccessors.fromApplication(context, Entry::class.java).dayTickHandler()
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(TIMEOUT) { handler.handle(kind) }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION_TICK = "io.github.swashbuckler104.budgetwidget.notifications.DAY_TICK"
        const val ACTION_NUDGE = "io.github.swashbuckler104.budgetwidget.notifications.NUDGE"
        private val TIMEOUT = 8.seconds
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
