package io.github.swashbuckler104.budgetwidget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent

/**
 * The Insight widget (SCOPE.md F5): today, this month vs last month so far, a 7-day bar chart and
 * the top 3 categories. Exact size mode, so the chart bitmap matches the real size. Tap opens
 * the Analysis tab.
 */
class InsightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loader = WidgetEntryPoint.get(context).insightLoader()
        // Loaded before composing, so the first frame already shows real data.
        val initial = loader.load()
        val open = analysisIntent(context)
        provideContent {
            val snapshot by produceState(initial) { loader.observe().collect { value = it } }
            WidgetTheme { InsightWidgetContent(snapshot, open) }
        }
    }

    companion object {
        /**
         * The app's Analysis tab, through its navigation deep link. A running app is brought to
         * the front and switches tab (CLEAR_TOP + SINGLE_TOP deliver it to `onNewIntent`), so an
         * open sheet or form in the other tab keeps its state.
         */
        fun analysisIntent(context: Context): Intent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                data = ANALYSIS_URI.toUri()
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }

        /** Must match the deep link on the Analysis destination in `:app`. */
        const val ANALYSIS_URI = "budgetwidget://analysis"
    }
}

class InsightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InsightWidget()
}
