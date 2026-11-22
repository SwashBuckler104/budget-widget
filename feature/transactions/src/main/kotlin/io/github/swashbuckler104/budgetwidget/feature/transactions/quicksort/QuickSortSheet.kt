package io.github.swashbuckler104.budgetwidget.feature.transactions.quicksort

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.swashbuckler104.budgetwidget.core.data.sort.MerchantScope
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.BudgetBottomSheet
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryChip
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryPicker
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BudgetTheme
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import io.github.swashbuckler104.budgetwidget.core.model.formatSignedInr
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
    // The shared sheet keeps the category list's scroll inside it (ADR-0041): before, a small
    // scroll that hit the top moved the sheet itself, which then settled or closed.
    BudgetBottomSheet(onDismissRequest = onClose, modifier = modifier) {
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
    /** (category, note, remember the merchant, what else goes there for the merchant) */
    onChoose: (Category, String, Boolean, MerchantScope) -> Unit,
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
    var earlier by rememberSaveable(transaction.id) { mutableStateOf(false) }
    var newer by rememberSaveable(transaction.id) { mutableStateOf(false) }
    var showAll by rememberSaveable(transaction.id) { mutableStateOf(false) }
    val hasMerchant = transaction.merchantKey != null
    val choose = { category: Category ->
        val scope = if (hasMerchant) MerchantScope(earlier = earlier, newer = newer) else MerchantScope.NONE
        onChoose(category, note, (remember || newer) && hasMerchant, scope)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // No scroll or insets here: the sheet scrolls this and applies the insets.
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
        if (hasMerchant) {
            val merchant = transaction.merchantDisplay.orEmpty()
            SwitchLine(stringResource(R.string.quicksort_remember, merchant), null, remember || newer) {
                remember = it
                if (!it) newer = false
            }
            // ADR-0062: two separate choices for the merchant's other payments.
            CheckLine(stringResource(R.string.quicksort_earlier), earlier) { earlier = it }
            CheckLine(stringResource(R.string.quicksort_newer), newer) { newer = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onIgnore, enabled = !state.working) { Text(stringResource(R.string.quicksort_ignore)) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.quicksort_skip)) }
        }
    }
}

/**
 * A labelled switch where the whole row is the switch: one large target, and TalkBack reads the
 * label with the on/off state (the switch alone had no name).
 */
@Composable
private fun SwitchLine(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A labelled checkbox where the whole row is the target, like [SwitchLine]. */
@Composable
internal fun CheckLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
private fun TransactionSummary(transaction: Transaction, overdue: Boolean, modifier: Modifier = Modifier) {
    val incoming = transaction.direction == Direction.IN
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatSignedInr(transaction.amountPaise, transaction.direction),
                style = MaterialTheme.typography.headlineMedium,
                color = if (incoming) BudgetTheme.money.income else BudgetTheme.money.spend,
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
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.quicksort_all_sorted), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onClose) { Text(stringResource(R.string.quicksort_done)) }
    }
}

private const val NOTE_LIMIT = 500
