package io.github.swashbuckler104.budgetwidget.feature.transactions.quicksort

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryChip
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryPicker
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import io.github.swashbuckler104.budgetwidget.feature.transactions.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The Quick-Sort bottom sheet (SCOPE.md F3/F4). [onClose] is called when it should go away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSortRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickSortViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Show the sheet only once its first content is complete: a sheet that opens nearly empty and
    // then grows (slow cold start) can stay stuck below the screen edge. The window is transparent,
    // so waiting shows nothing.
    var ready by rememberSaveable { mutableStateOf(false) }
    if (!ready && !state.loading && (state.transaction == null || state.suggestionsReady)) ready = true
    if (!ready) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, modifier = modifier) {
        QuickSortContent(
            state = state,
            onChoose = viewModel::choose,
            onIgnore = viewModel::ignore,
            onSkip = viewModel::skip,
            onClose = onClose,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickSortContent(
    state: QuickSortUiState,
    onChoose: (Category, String, Boolean) -> Unit,
    onIgnore: () -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transaction = state.transaction
    val currentOnClose by rememberUpdatedState(onClose)
    if (state.loading) return
    if (state.finished || transaction == null) {
        // A single opened transaction closes as soon as it's sorted; the inbox shows a done state.
        if (state.singleTransaction) {
            LaunchedEffect(Unit) { currentOnClose() }
        } else {
            AllSorted(onClose, modifier)
        }
        return
    }
    // Per-transaction inputs reset when the next transaction comes up.
    var note by rememberSaveable(transaction.id) { mutableStateOf("") }
    var remember by rememberSaveable(transaction.id) { mutableStateOf(true) }
    var showAll by rememberSaveable(transaction.id) { mutableStateOf(false) }
    val choose = { category: Category -> onChoose(category, note, remember && transaction.merchantKey != null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.quicksort_remaining, state.remaining, state.remaining),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TransactionSummary(transaction, state.overdue)
        Text(
            stringResource(R.string.quicksort_suggested),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics {
                heading()
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.suggestions.forEach {
                CategoryChip(it, onClick = { choose(it) }, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        TextButton(onClick = { showAll = !showAll }) {
            Text(stringResource(if (showAll) R.string.quicksort_hide_all else R.string.quicksort_show_all))
        }
        if (showAll) CategoryPicker(state.groups, onPick = choose)
        HorizontalDivider()
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(NOTE_LIMIT) },
            label = { Text(stringResource(R.string.quicksort_note)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (transaction.merchantKey != null) {
            val rememberLabel = stringResource(R.string.quicksort_remember, transaction.merchantDisplay.orEmpty())
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(rememberLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = remember,
                    onCheckedChange = { remember = it },
                    modifier = Modifier.semantics {
                        stateDescription =
                            rememberLabel
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onIgnore, enabled = !state.working) { Text(stringResource(R.string.quicksort_ignore)) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.quicksort_skip)) }
        }
    }
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
private fun TransactionSummary(transaction: Transaction, overdue: Boolean, modifier: Modifier = Modifier) {
    val incoming = transaction.direction == Direction.IN
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                (if (incoming) "+" else "-") + formatInr(transaction.amountPaise),
                style = MaterialTheme.typography.headlineMedium,
                color = if (incoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (overdue) {
                Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text(stringResource(R.string.quicksort_overdue))
                }
            }
        }
        Text(
            transaction.merchantDisplay ?: stringResource(R.string.transactions_unknown_merchant),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val account = listOfNotNull(transaction.bankName, transaction.accountLast4?.let { "··$it" }).joinToString(" ")
        val time = timeFormat.format(Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault()))
        Text(
            "$account · $time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (transaction.duplicateOfId != null) {
            Text(
                stringResource(R.string.quicksort_possible_duplicate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun AllSorted(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.quicksort_all_sorted), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onClose) { Text(stringResource(R.string.quicksort_done)) }
    }
}

private const val NOTE_LIMIT = 500
