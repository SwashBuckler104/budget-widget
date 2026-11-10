package io.github.swashbuckler104.budgetwidget.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * The Quick-Sort widget (SCOPE.md F4): sort the inbox from the home screen without opening the
 * app. Each instance remembers its current transaction in its Glance state.
 */
class QuickSortWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, STANDARD, LARGE))

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = WidgetEntryPoint.get(context)
        val loader = entry.loader()
        val launcher = entry.launcher()
        // Loaded before composing, so the first frame already shows real data.
        val initial = loader.load(getAppWidgetState<Preferences>(context, stateDefinition, id)[CURRENT])
        val intents = WidgetIntents(
            inbox = launcher.inbox(context),
            transaction = { launcher.transaction(context, it) },
            app = context.packageManager.getLaunchIntentForPackage(context.packageName),
        )
        provideContent {
            val current = currentState(CURRENT)
            val snapshot by produceState(initial, current) { loader.observe(current).collect { value = it } }
            WidgetTheme { QuickSortWidgetContent(snapshot, intents) }
        }
    }

    companion object {
        /** This instance's current transaction id; absent = the oldest. */
        internal val CURRENT = longPreferencesKey("current_transaction")

        // Responsive breakpoints. 4 cells are about 250 dp wide on most phones.
        internal val SMALL = DpSize(110.dp, 110.dp)
        internal val WIDE = DpSize(250.dp, 110.dp)
        internal val STANDARD = DpSize(250.dp, 180.dp)
        internal val LARGE = DpSize(250.dp, 240.dp)
    }
}

class QuickSortWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickSortWidget()
}
