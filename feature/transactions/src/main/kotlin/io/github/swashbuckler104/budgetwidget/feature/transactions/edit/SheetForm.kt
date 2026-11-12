package io.github.swashbuckler104.budgetwidget.feature.transactions.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * A sheet's form values, saved by the composable that shows the sheet. A `ModalBottomSheet` is a
 * separate window whose own saved state is dropped, so `rememberSaveable` inside it lost typed
 * text when the tab was left (an Insight widget tap) or the process died. Values must be
 * bundle-able: strings, numbers, booleans, enums.
 */
@Stable
internal class SheetForm(private val values: SnapshotStateMap<String, Any?>) {
    /** The field [key], starting at [initial] the first time. */
    fun <T> field(key: String, initial: () -> T): MutableState<T> {
        if (key !in values) values[key] = initial()
        return Field(key)
    }

    private inner class Field<T>(private val key: String) : MutableState<T> {
        @Suppress("UNCHECKED_CAST")
        override var value: T
            get() = values[key] as T
            set(v) {
                values[key] = v
            }

        override fun component1(): T = value

        override fun component2(): (T) -> Unit = { value = it }
    }

    companion object {
        val Saver: Saver<SheetForm, HashMap<String, Any?>> = Saver(
            save = { HashMap(it.values) },
            restore = { saved -> SheetForm(mutableStateMapOf<String, Any?>().apply { putAll(saved) }) },
        )
    }
}

/** A [SheetForm] for [key] (a transaction id, or a constant for the add sheet). */
@Composable
internal fun rememberSheetForm(key: Any): SheetForm =
    rememberSaveable(key, saver = SheetForm.Saver) { SheetForm(mutableStateMapOf()) }
