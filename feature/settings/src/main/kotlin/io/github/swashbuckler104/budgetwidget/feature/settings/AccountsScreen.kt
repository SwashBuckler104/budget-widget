package io.github.swashbuckler104.budgetwidget.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.account.OpeningBalances
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.EmptyState
import io.github.swashbuckler104.budgetwidget.core.model.Account
import io.github.swashbuckler104.budgetwidget.core.model.AccountKind
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import io.github.swashbuckler104.budgetwidget.core.model.parseInrToPaise
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Accounts seen in bank SMS (SCOPE.md F2/F9): give them a nickname, or hide them from filters. */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val openingBalances: OpeningBalances,
) : ViewModel() {
    /** Null while loading. */
    val accounts: StateFlow<List<Account>?> = transactions.observeAccounts()
        .map<List<Account>, List<Account>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Account id to the balance it started with (ADR-0059). */
    val openings: StateFlow<Map<Long, Long>> = openingBalances.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** [balancePaise] null removes it; negative is money owed. */
    fun setOpeningBalance(id: Long, balancePaise: Long?) {
        viewModelScope.launch { openingBalances.set(id, balancePaise) }
    }

    fun rename(id: Long, nickname: String) {
        viewModelScope.launch { transactions.setAccountNickname(id, nickname.take(MAX_NICKNAME)) }
    }

    fun setHidden(id: Long, hidden: Boolean) {
        viewModelScope.launch { transactions.setAccountHidden(id, hidden) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_NICKNAME = 30
    }
}

@Composable
fun AccountsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AccountsViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val openings by viewModel.openings.collectAsStateWithLifecycle()
    AccountsScreen(
        accounts = accounts,
        onBack = onBack,
        onRename = viewModel::rename,
        onToggleVisibility = viewModel::setHidden,
        openings = openings,
        onSetOpening = viewModel::setOpeningBalance,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsScreen(
    accounts: List<Account>?,
    onBack: () -> Unit,
    onRename: (Long, String) -> Unit,
    onToggleVisibility: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    openings: Map<Long, Long> = emptyMap(),
    onSetOpening: (Long, Long?) -> Unit = { _, _ -> },
) {
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_accounts)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        when {
            accounts == null -> Unit

            accounts.isEmpty() -> EmptyState(
                icon = Icons.Rounded.AccountBalance,
                title = stringResource(R.string.settings_accounts_empty_title),
                body = stringResource(R.string.settings_accounts_empty_body),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(modifier = Modifier.padding(padding)) {
                item {
                    Text(
                        stringResource(R.string.settings_accounts_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(accounts, key = { it.id }) { account ->
                    AccountRow(account, openings[account.id], onEdit = {
                        editing = account.id
                    }, onToggleVisibility = { onToggleVisibility(account.id, it) })
                }
            }
        }
    }
    val account = editing?.let { id -> accounts?.firstOrNull { it.id == id } }
    if (account != null) {
        EditAccountDialog(
            account = account,
            opening = openings[account.id],
            onSave = { nickname, opening ->
                if (nickname != account.nickname.orEmpty()) onRename(account.id, nickname)
                // Only when it changed: setting it also resets the account's balance to it.
                if (opening != openings[account.id]) onSetOpening(account.id, opening)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun AccountRow(account: Account, opening: Long?, onEdit: () -> Unit, onToggleVisibility: (Boolean) -> Unit) {
    val shown = stringResource(R.string.settings_account_shown)
    val hiddenText = stringResource(R.string.settings_account_hidden)
    val toggle = stringResource(R.string.settings_account_show_in_filters, account.label)
    ListItem(
        leadingContent = {
            Icon(
                if (account.kind == AccountKind.CARD) Icons.Rounded.CreditCard else Icons.Rounded.AccountBalance,
                contentDescription = null,
            )
        },
        headlineContent = { Text(account.label) },
        supportingContent = {
            val details = listOfNotNull(
                account.bankName.takeIf { account.nickname != null }?.let { bank ->
                    listOfNotNull(bank, account.last4?.let { "··$it" }).joinToString(" ")
                },
                account.balancePaise?.let { stringResource(R.string.settings_account_balance, formatInr(it)) },
                opening?.let {
                    if (it < 0) {
                        stringResource(R.string.settings_account_opening_owed, formatInr(-it))
                    } else {
                        stringResource(R.string.settings_account_opening, formatInr(it))
                    }
                },
            )
            if (details.isNotEmpty()) Text(details.joinToString(" · "))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.settings_account_edit, account.label),
                    )
                }
                Switch(
                    checked = !account.hidden,
                    onCheckedChange = { onToggleVisibility(!it) },
                    modifier = Modifier.semantics {
                        contentDescription = toggle
                        stateDescription = if (account.hidden) hiddenText else shown
                    },
                )
            }
        },
    )
}

/**
 * Nickname and starting balance (ADR-0059). A card's starting balance is what was owed, typed as a
 * positive amount and stored negative; an empty field removes it.
 */
@Composable
private fun EditAccountDialog(
    account: Account,
    opening: Long?,
    onSave: (nickname: String, opening: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val card = account.kind == AccountKind.CARD
    var text by rememberSaveable { mutableStateOf(account.nickname.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(opening?.let { formatRupees(kotlin.math.abs(it)) }.orEmpty()) }
    val parsed = parseInrToPaise(amount)
    val amountError = amount.isNotBlank() && parsed == null
    val amountLabel = stringResource(
        if (card) R.string.settings_account_opening_owed_label else R.string.settings_account_opening_label,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_account_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(AccountsViewModel.MAX_NICKNAME) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_account_nickname)) },
                    placeholder = {
                        Text(listOfNotNull(account.bankName, account.last4?.let { "··$it" }).joinToString(" "))
                    },
                    supportingText = { Text(stringResource(R.string.settings_account_nickname_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(AMOUNT_LENGTH) },
                    singleLine = true,
                    isError = amountError,
                    label = { Text(amountLabel) },
                    prefix = { Text("₹") },
                    supportingText = { Text(stringResource(R.string.settings_account_opening_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !amountError, onClick = { onSave(text, parsed?.let { if (card) -it else it }) }) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** Paise as plain rupees for an input field: `52340`, `99.5`. */
private fun formatRupees(paise: Long): String =
    if (paise % 100 == 0L) (paise / 100).toString() else "%d.%02d".format(paise / 100, paise % 100)

private const val AMOUNT_LENGTH = 20

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.settings_back))
    }
}
