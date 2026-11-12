package io.github.swashbuckler104.budgetwidget.feature.transactions.list

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.swashbuckler104.budgetwidget.core.data.edit.EditResult
import io.github.swashbuckler104.budgetwidget.core.data.list.DayGroup
import io.github.swashbuckler104.budgetwidget.core.data.list.TransactionFilter
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortResult
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.EmptyState
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.SectionHeader
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.feature.transactions.R
import io.github.swashbuckler104.budgetwidget.feature.transactions.edit.AddTransactionSheet
import io.github.swashbuckler104.budgetwidget.feature.transactions.edit.EditActions
import io.github.swashbuckler104.budgetwidget.feature.transactions.edit.EditTransactionSheet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
private const val BACKFILL_DAYS = 30
private const val INBOX_PREVIEW = 5

/**
 * Tab 2 (SCOPE.md F7): the inbox pinned on top, then every transaction by day. With [onBack] it
 * is the Analysis drill-down: a fixed category and month, titled [title].
 */
@Composable
fun TransactionsRoute(
    onOpenQuickSort: (transactionId: Long?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    title: String? = null,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var smsGranted by remember {
        mutableStateOf(
            SMS_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED },
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        smsGranted = result.values.all { it }
        if (smsGranted) viewModel.syncNow()
    }
    val snackbar = remember { SnackbarHostState() }
    val messageText = messageTexts()
    val undoLabel by rememberUpdatedState(stringResource(R.string.transactions_undo))
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            // A newer action replaces the snackbar at once instead of queueing behind a long Undo.
            snackbar.currentSnackbarData?.dismiss()
            val undo = message.undo
            val result = snackbar.showSnackbar(
                message = messageText(message),
                actionLabel = if (undo != null) undoLabel else null,
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = undo == null,
            )
            if (result == SnackbarResult.ActionPerformed && undo != null) viewModel.undo(undo)
        }
    }

    var adding by rememberSaveable { mutableStateOf(false) }
    // Survives rotation: the ids being categorized (null when the picker is closed).
    var pickingIds by rememberSaveable { mutableStateOf<List<Long>?>(null) }
    var pickingBulk by rememberSaveable { mutableStateOf(false) }

    TransactionsScreen(
        state = state,
        title = title,
        snackbar = snackbar,
        smsGranted = smsGranted,
        callbacks = ScreenCallbacks(
            onBack = onBack,
            onFilter = viewModel::setFilter,
            onClearFilters = viewModel::clearFilters,
            onOpenQuickSort = onOpenQuickSort,
            onOpenSettings = onOpenSettings,
            onEdit = viewModel::openEditor,
            onToggleSelection = viewModel::toggleSelection,
            onSelectAll = viewModel::selectAll,
            onClearSelection = viewModel::clearSelection,
            onCategorize = { ids, bulk ->
                pickingIds = ids
                pickingBulk = bulk
            },
            onIgnore = viewModel::ignore,
            onUnignore = viewModel::unignore,
            onMerge = viewModel::merge,
            onKeepBoth = viewModel::keepBoth,
            onAdd = { adding = true },
            onRequestSms = { launcher.launch(SMS_PERMISSIONS) },
            onSyncNow = viewModel::syncNow,
            onBackfill = { viewModel.backfill(BACKFILL_DAYS) },
        ),
        modifier = modifier,
    )

    editing?.let { detail ->
        EditTransactionSheet(
            detail = detail,
            categories = state.categories,
            groups = state.groups,
            actions = EditActions(
                onSave = viewModel::save,
                onIgnore = {
                    viewModel.ignore(listOf(detail.transaction.id))
                    viewModel.openEditor(null)
                },
                onUnignore = {
                    viewModel.unignore(detail.transaction.id)
                    viewModel.openEditor(null)
                },
                onDelete = { viewModel.delete(detail.transaction.id) },
                onReportWrongAmount = { viewModel.reportWrongAmount(detail.transaction.id) },
                onDismiss = { viewModel.openEditor(null) },
            ),
        )
    }
    if (adding) {
        AddTransactionSheet(
            groups = state.groups,
            categories = state.categories,
            nowMillis = remember { System.currentTimeMillis() },
            onAdd = { viewModel.add(it) { adding = false } },
            onDismiss = { adding = false },
        )
    }
    pickingIds?.let { ids ->
        // Every leaf that fits the selection's direction; all leaves for a mixed selection.
        val direction = (state.inbox.map { it.transaction } + state.days.flatMap { it.transactions })
            .filter { it.id in ids }
            .map { it.direction }
            .distinct()
            .singleOrNull()
        PickCategorySheet(
            title = pluralStringResource(R.plurals.transactions_categorize_n, ids.size, ids.size),
            groups = state.groups,
            direction = direction,
            onPick = { category ->
                viewModel.categorize(ids, category.id, pickingBulk)
                pickingIds = null
            },
            onDismiss = { pickingIds = null },
        )
    }
}

internal class ScreenCallbacks(
    val onBack: (() -> Unit)?,
    val onFilter: ((TransactionFilter) -> TransactionFilter) -> Unit,
    val onClearFilters: () -> Unit,
    val onOpenQuickSort: (Long?) -> Unit,
    val onOpenSettings: () -> Unit,
    val onEdit: (Long) -> Unit,
    val onToggleSelection: (Long) -> Unit,
    /** Selects these ids: everything currently shown. */
    val onSelectAll: (List<Long>) -> Unit,
    val onClearSelection: () -> Unit,
    val onCategorize: (ids: List<Long>, bulk: Boolean) -> Unit,
    val onIgnore: (List<Long>) -> Unit,
    val onUnignore: (Long) -> Unit,
    val onMerge: (Long) -> Unit,
    val onKeepBoth: (Long) -> Unit,
    val onAdd: () -> Unit,
    val onRequestSms: () -> Unit,
    val onSyncNow: () -> Unit,
    val onBackfill: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionsScreen(
    state: TransactionsUiState,
    title: String?,
    snackbar: SnackbarHostState,
    smsGranted: Boolean,
    callbacks: ScreenCallbacks,
    modifier: Modifier = Modifier,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var inboxExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.selecting, onBack = callbacks.onClearSelection)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (state.selecting) {
                SelectionBar(state, shownIds(state, inboxExpanded), callbacks)
            } else {
                TopAppBar(
                    title = { Text(title ?: stringResource(R.string.transactions_title)) },
                    navigationIcon = {
                        callbacks.onBack?.let { back ->
                            IconButton(onClick = back) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.transactions_back))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = !searching }) {
                            Icon(Icons.Rounded.Search, stringResource(R.string.transactions_search))
                        }
                        if (callbacks.onBack == null) OverflowMenu(smsGranted, callbacks)
                    },
                )
            }
        },
        floatingActionButton = {
            if (!state.selecting && !state.drillDown) {
                ExtendedFloatingActionButton(
                    onClick = callbacks.onAdd,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.transactions_add)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searching || state.filter.query.isNotEmpty()) {
                SearchField(state.filter.query, { q -> callbacks.onFilter { it.copy(query = q) } }) {
                    callbacks.onFilter { it.copy(query = "") }
                    searching = false
                }
            }
            if (!state.drillDown) {
                FilterBar(
                    filter = state.filter,
                    categories = state.categories,
                    groups = state.groups,
                    accounts = state.accounts,
                    onFilter = callbacks.onFilter,
                    onClear = callbacks.onClearFilters,
                )
            }
            TransactionList(state, smsGranted, inboxExpanded, { inboxExpanded = it }, callbacks)
        }
    }
}

/** Ids on screen in display order: the inbox (as far as it is expanded), then the days. */
private fun shownIds(state: TransactionsUiState, inboxExpanded: Boolean): List<Long> =
    (if (inboxExpanded) state.inbox else state.inbox.take(INBOX_PREVIEW)).map { it.transaction.id } +
        state.days.flatMap { day -> day.transactions.map { it.id } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(state: TransactionsUiState, shown: List<Long>, callbacks: ScreenCallbacks) {
    val ids = state.selection.toList()
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.transactions_selected, ids.size, ids.size)) },
        navigationIcon = {
            IconButton(onClick = callbacks.onClearSelection) {
                Icon(Icons.Rounded.Close, stringResource(R.string.transactions_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = { callbacks.onSelectAll(shown) }) {
                Icon(Icons.Rounded.SelectAll, stringResource(R.string.transactions_select_all))
            }
            IconButton(onClick = { callbacks.onCategorize(ids, true) }) {
                Icon(Icons.Rounded.Sell, stringResource(R.string.transactions_categorize))
            }
            IconButton(onClick = { callbacks.onIgnore(ids) }) {
                Icon(Icons.Rounded.DoNotDisturbOn, stringResource(R.string.transactions_ignore))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

@Composable
private fun OverflowMenu(smsGranted: Boolean, callbacks: ScreenCallbacks) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.transactions_more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (smsGranted) {
            DropdownMenuItem(text = { Text(stringResource(R.string.transactions_sync_now)) }, onClick = {
                callbacks.onSyncNow()
                open = false
            })
            DropdownMenuItem(
                text = { Text(pluralStringResource(R.plurals.transactions_backfill, BACKFILL_DAYS, BACKFILL_DAYS)) },
                onClick = {
                    callbacks.onBackfill()
                    open = false
                },
            )
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.transactions_settings)) }, onClick = {
            callbacks.onOpenSettings()
            open = false
        })
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    // The field owns its text: echoing the ViewModel's (asynchronous) filter state back into it
    // dropped and repeated characters while typing fast (found on the emulator).
    var text by rememberSaveable { mutableStateOf(query) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onQuery(it)
        },
        placeholder = { Text(stringResource(R.string.transactions_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, stringResource(R.string.transactions_close_search))
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun TransactionList(
    state: TransactionsUiState,
    smsGranted: Boolean,
    inboxExpanded: Boolean,
    onInboxExpand: (Boolean) -> Unit,
    callbacks: ScreenCallbacks,
) {
    val zone = ZoneId.systemDefault()
    val today = remember { LocalDate.now(zone) }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val dateTimeFormat = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    fun time(t: Transaction) = Instant.ofEpochMilli(t.occurredAt).atZone(zone).format(timeFormat)
    fun dateTime(t: Transaction) = Instant.ofEpochMilli(t.occurredAt).atZone(zone).let {
        if (it.toLocalDate() == today) it.format(timeFormat) else it.format(dateTimeFormat)
    }
    val onRowClick: (Transaction) -> Unit = { t ->
        if (state.selecting) callbacks.onToggleSelection(t.id) else callbacks.onEdit(t.id)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
        if (!smsGranted && !state.drillDown) item(key = "sms") { SmsCard(callbacks.onRequestSms) }

        if (state.inbox.isNotEmpty()) {
            item(key = "inbox-header") {
                SectionHeader(
                    pluralStringResource(R.plurals.transactions_inbox, state.inbox.size, state.inbox.size),
                    trailing = {
                        FilledTonalButton(onClick = { callbacks.onOpenQuickSort(null) }) {
                            Text(stringResource(R.string.transactions_sort_all))
                        }
                    },
                )
            }
            val shown = if (inboxExpanded) state.inbox else state.inbox.take(INBOX_PREVIEW)
            items(shown, key = { "inbox-${it.transaction.id}" }) { entry ->
                val id = entry.transaction.id
                InboxCard(
                    entry = entry,
                    time = dateTime(entry.transaction),
                    duplicateTime = entry.duplicateOf?.let(::dateTime),
                    selected = id in state.selection,
                    onSort = {
                        if (state.selecting) callbacks.onToggleSelection(id) else callbacks.onOpenQuickSort(id)
                    },
                    onLongClick = { callbacks.onToggleSelection(id) },
                    onMerge = { callbacks.onMerge(id) },
                    onKeepBoth = { callbacks.onKeepBoth(id) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (state.inbox.size > INBOX_PREVIEW) {
                item(key = "inbox-more") {
                    TextButton(
                        onClick = { onInboxExpand(!inboxExpanded) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(
                            if (inboxExpanded) {
                                stringResource(R.string.transactions_show_less)
                            } else {
                                stringResource(R.string.transactions_show_all, state.inbox.size)
                            },
                        )
                    }
                }
            }
            item(key = "inbox-gap") { Spacer(Modifier.height(8.dp)) }
        }

        state.days.forEach { day ->
            stickyHeader(key = "day-${day.date}") { DayHeader(day, today) }
            items(day.transactions, key = { "tx-${it.id}" }) { t ->
                SwipeActions(
                    transaction = t,
                    enabled = !state.selecting,
                    onCategorize = { callbacks.onCategorize(listOf(t.id), false) },
                    onIgnoreOrRestore = {
                        if (t.status ==
                            TransactionStatus.IGNORED
                        ) {
                            callbacks.onUnignore(t.id)
                        } else {
                            callbacks.onIgnore(listOf(t.id))
                        }
                    },
                    modifier = Modifier.animateItem(),
                ) {
                    TransactionRow(
                        transaction = t,
                        category = t.categoryId?.let(state.categories::get),
                        time = time(t),
                        selected = t.id in state.selection,
                        onClick = { onRowClick(t) },
                        onLongClick = { callbacks.onToggleSelection(t.id) },
                    )
                }
            }
        }

        if (!state.loading && state.inbox.isEmpty() && state.days.isEmpty()) {
            item(key = "empty") {
                if (state.total == 0) {
                    EmptyState(
                        icon = Icons.Rounded.Inbox,
                        title = stringResource(R.string.transactions_empty_title),
                        body = stringResource(R.string.transactions_empty_body),
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.transactions_no_match),
                        action = if (state.drillDown) {
                            null
                        } else {
                            {
                                TextButton(onClick = callbacks.onClearFilters) {
                                    Text(stringResource(R.string.filter_clear))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup, today: LocalDate) {
    val label = when (day.date) {
        today -> stringResource(R.string.transactions_today)

        today.minusDays(1) -> stringResource(R.string.transactions_yesterday)

        else -> day.date.format(
            DateTimeFormatter.ofPattern(if (day.date.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"),
        )
    }
    SectionHeader(
        label,
        trailing = { DayTotal(day.spentPaise, day.incomePaise) },
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SmsCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Rounded.Sms, contentDescription = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.transactions_sms_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRequest) { Text(stringResource(R.string.transactions_sms_allow)) }
            }
        }
    }
}

/** Snackbar text for each [TransactionsMessage]. */
@Composable
private fun messageTexts(): (TransactionsMessage) -> String {
    val resources by rememberUpdatedState(LocalResources.current)
    return { message ->
        val r = resources
        when (message) {
            is TransactionsMessage.Sorted -> {
                if (message.skipped == 0) {
                    r.getQuantityString(R.plurals.transactions_msg_sorted, message.count, message.count)
                } else {
                    r.getQuantityString(R.plurals.transactions_msg_sorted_count, message.count, message.count) + " · " +
                        r.getQuantityString(R.plurals.transactions_msg_skipped, message.skipped, message.skipped)
                }
            }

            is TransactionsMessage.NoneFit -> r.getString(R.string.transactions_msg_mismatch)

            is TransactionsMessage.Renamed -> r.getQuantityString(
                R.plurals.transactions_msg_renamed,
                message.count,
                message.count,
            )

            is TransactionsMessage.Ignored -> r.getQuantityString(
                R.plurals.transactions_msg_ignored,
                message.count,
                message.count,
            )

            is TransactionsMessage.Merged -> r.getString(R.string.transactions_msg_merged)

            is TransactionsMessage.KeptBoth -> r.getString(R.string.transactions_msg_kept_both)

            is TransactionsMessage.BackInInbox -> r.getString(R.string.transactions_msg_back_in_inbox)

            is TransactionsMessage.Deleted -> r.getString(R.string.transactions_msg_deleted)

            TransactionsMessage.Saved -> r.getString(R.string.transactions_msg_saved)

            TransactionsMessage.Added -> r.getString(R.string.transactions_msg_added)

            TransactionsMessage.Reported -> r.getString(R.string.transactions_msg_reported)

            is TransactionsMessage.Failed -> {
                val result = message.result
                when {
                    result == EditResult.InvalidAmount -> r.getString(R.string.edit_amount_invalid)

                    result == EditResult.CategoryMismatch ||
                        (result is EditResult.SortFailed && result.result == SortResult.KindMismatch) ->
                        r.getString(R.string.transactions_msg_mismatch)

                    else -> r.getString(R.string.transactions_msg_failed)
                }
            }
        }
    }
}
