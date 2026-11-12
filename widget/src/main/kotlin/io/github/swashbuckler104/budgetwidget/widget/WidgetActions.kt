package io.github.swashbuckler104.budgetwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortResult
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason

internal object WidgetKeys {
    val transaction = ActionParameters.Key<Long>("transactionId")
    val category = ActionParameters.Key<Long>("categoryId")
    val delta = ActionParameters.Key<Int>("delta")
}

/** Stores [id] as the instance's current transaction (per widget, SCOPE.md F4). */
private suspend fun setCurrent(context: Context, glanceId: GlanceId, id: Long?) {
    updateAppWidgetState(context, glanceId) { prefs ->
        if (id == null) prefs.remove(QuickSortWidget.CURRENT) else prefs[QuickSortWidget.CURRENT] = id
    }
}

/**
 * Sorts or ignores the card's transaction and advances to the next one. `requirePending`: the
 * widget may show a transaction that was sorted elsewhere a moment ago; that tap does nothing.
 */
private suspend fun finish(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
    sort: suspend (WidgetEntryPoint, Long) -> SortResult,
) {
    val id = parameters[WidgetKeys.transaction] ?: return
    val entry = WidgetEntryPoint.get(context)
    val order = entry.loader().pendingIds()
    if (sort(entry, id) == SortResult.Done) setCurrent(context, glanceId, WidgetNavigation.afterRemoving(order, id))
    // Every instance shows the same inbox: redraw all of them (also refreshes stale chips).
    entry.widgets().refreshNow()
}

/** A category chip: sort into that category; the merchant rule learns, like the sheet's default. */
internal class SortChipAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val category = parameters[WidgetKeys.category] ?: return
        finish(context, glanceId, parameters) { entry, id ->
            entry.sort().categorize(id, category, requirePending = true)
        }
    }
}

internal class IgnoreAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        finish(context, glanceId, parameters) { entry, id ->
            entry.sort().ignore(id, IgnoreReason.USER, requirePending = true)
        }
    }
}

/** ‹ and ›: show the previous or next pending transaction in this instance only. */
internal class StepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[WidgetKeys.delta] ?: return
        val current = parameters[WidgetKeys.transaction]
        val entry = WidgetEntryPoint.get(context)
        setCurrent(context, glanceId, WidgetNavigation.step(entry.loader().pendingIds(), current, delta))
        QuickSortWidget().update(context, glanceId)
    }
}
