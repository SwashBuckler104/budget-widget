package io.github.swashbuckler104.budgetwidget.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import io.github.swashbuckler104.budgetwidget.core.designsystem.R

/**
 * Every bottom sheet in the app (docs/DECISIONS.md ADR-0041). A pinned [header] (title and the
 * primary action, e.g. Save), a scrolling [content], and an optional pinned [footer].
 *
 * Material's `ModalBottomSheet` takes whatever scroll and fling its content leaves over: scrolling
 * back to the top a little too hard moved the sheet, and with only Expanded and Hidden anchors it
 * then closed (losing typed input) or bounced between the two. Here, once [content] is taller than
 * the sheet, its leftover scroll and fling are consumed before they reach the sheet, so only the
 * drag handle and the header move it. A short sheet keeps normal swipe-to-dismiss.
 *
 * With [hasUnsavedChanges], swiping it away, tapping outside or pressing Back asks "Discard
 * changes?" instead of closing. Insets (navigation bar, keyboard) are applied by the sheet itself;
 * callers must not add `navigationBarsPadding()` or `imePadding()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    hasUnsavedChanges: Boolean = false,
    header: @Composable ColumnScope.() -> Unit = {},
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var askDiscard by remember { mutableStateOf(false) }
    val unsaved by rememberUpdatedState(hasUnsavedChanges)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && unsaved) {
                askDiscard = true
                false
            } else {
                true
            }
        },
    )
    val scroll = rememberScrollState()
    ModalBottomSheet(
        // Back always ends up here, even when the hide was vetoed above.
        onDismissRequest = { if (unsaved) askDiscard = true else onDismissRequest() },
        sheetState = sheetState,
        // The status bar is kept outside the sheet, never padded inside it. Material pads the
        // content with the part of the top inset the sheet overlaps, so a nearly full-height sheet
        // that overshot into the status bar after a fling grew, which moved its Expanded anchor,
        // which moved it again: it shook for about a second (ADR-0057).
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        header()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .then(if (scroll.maxValue > 0) Modifier.nestedScroll(KeepScrollInContent) else Modifier)
                .verticalScroll(scroll),
            content = content,
        )
        footer?.invoke(this)
        if (askDiscard) {
            AlertDialog(
                onDismissRequest = { askDiscard = false },
                title = { Text(stringResource(R.string.sheet_discard_title)) },
                text = { Text(stringResource(R.string.sheet_discard_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        askDiscard = false
                        onDismissRequest()
                    }) { Text(stringResource(R.string.sheet_discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { askDiscard = false }) { Text(stringResource(R.string.sheet_keep_editing)) }
                },
            )
        }
    }
}

/**
 * Sits between the scrolling content and the sheet: whatever the content could not scroll (it hit
 * its top or bottom) is consumed here instead of dragging or settling the sheet.
 */
private object KeepScrollInContent : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(0f, available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity(0f, available.y)
}
