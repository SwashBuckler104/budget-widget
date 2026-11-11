package io.github.swashbuckler104.budgetwidget.core.model

/** Which way money moved, seen from the user's side. */
enum class Direction { OUT, IN }

enum class TransactionSource { SMS, MANUAL }

/** `PENDING` needs sorting; the other two are final until the user edits the transaction. */
enum class TransactionStatus { PENDING, CATEGORIZED, IGNORED }

enum class IgnoreReason { USER, DUPLICATE }

/** Our copy of the parser's transaction type, so no module outside `:ingest` sees the parser. */
enum class ParserType { INCOME, EXPENSE, CREDIT, TRANSFER, INVESTMENT }

enum class AccountKind { BANK, CARD, WALLET }

enum class CategoryKind { EXPENSE, INCOME, TRANSFER }

/**
 * One money movement. Amounts are paise (1/100 INR) and always positive; [direction] gives the
 * sign. [occurredAt] is a UTC instant in epoch milliseconds.
 */
data class Transaction(
    val id: Long,
    val source: TransactionSource,
    val amountPaise: Long,
    val direction: Direction,
    val parserType: ParserType?,
    val merchantDisplay: String?,
    val bankName: String?,
    val accountLast4: String?,
    val occurredAt: Long,
    val isCard: Boolean,
    val status: TransactionStatus,
    val isBackfill: Boolean,
    val duplicateOfId: Long?,
    val merchantKey: String? = null,
    val categoryId: Long? = null,
    val autoCategorized: Boolean = false,
    val note: String? = null,
    val accountId: Long? = null,
    val ignoreReason: IgnoreReason? = null,
    val editedByUser: Boolean = false,
)

/** What the edit sheet shows beyond the list row: the original SMS (financial messages only). */
data class TransactionDetail(
    val transaction: Transaction,
    val smsSender: String?,
    val smsBody: String?,
    val reference: String?,
    val balanceAfterPaise: Long?,
)

/** A bank account, card or wallet, auto-created from SMS (SCOPE.md F2). */
data class Account(
    val id: Long,
    val bankName: String,
    val last4: String?,
    val nickname: String?,
    val kind: AccountKind,
    val hidden: Boolean,
    /** The last balance a bank SMS reported, in paise; null if none did. */
    val balancePaise: Long? = null,
) {
    /** `Nickname`, or `HDFC Bank ··1234`. */
    val label: String get() = nickname ?: listOfNotNull(bankName, last4?.let { "··$it" }).joinToString(" ")
}
