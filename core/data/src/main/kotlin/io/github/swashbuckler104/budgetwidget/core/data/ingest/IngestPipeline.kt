package io.github.swashbuckler104.budgetwidget.core.data.ingest

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.data.MerchantKeyNormalizer
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.TransferSeeds
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.sort.AutoSortSetting
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.dao.UnparsedSmsDao
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.UnparsedSmsEntity
import io.github.swashbuckler104.budgetwidget.core.model.AccountKind
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.sms.FlowHint
import io.github.swashbuckler104.budgetwidget.core.model.sms.ParsedSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class IngestOrigin {
    /** The SMS_RECEIVED broadcast. */
    REALTIME,

    /** The periodic or on-start inbox scan. */
    RECONCILE,

    /** A user-requested import of older inbox messages. */
    BACKFILL,
}

sealed interface IngestOutcome {
    /** Not a known bank sender: nothing was stored. */
    data object NotFinancial : IngestOutcome

    /** [autoSorted]: categorized from a merchant rule confirmed 3+ times (auto-sort setting). */
    data class Inserted(val id: Long, val duplicateOfId: Long?, val autoSorted: Boolean = false) : IngestOutcome

    /** The same SMS was already stored (same `txnHash`): a no-op. */
    data object AlreadyStored : IngestOutcome

    data object BalanceUpdated : IngestOutcome

    data class Unparsed(val reason: UnparsedReason) : IngestOutcome

    /** From a scan that started before the data was replaced (restore, start fresh): dropped. */
    data object Superseded : IngestOutcome
}

/**
 * The single, idempotent ingestion path for every SMS source (SCOPE.md F2). Calls are
 * serialized with a [Mutex] and each one runs in a database transaction, so the receiver, the
 * reconcile scan and backfill can race without creating duplicates.
 */
@Singleton
class IngestPipeline @Inject constructor(
    private val parser: SmsParser,
    private val db: BudgetDatabase,
    private val clock: WallClock,
    private val autoSort: AutoSortSetting,
    private val events: InboxEvents,
    private val dismissals: UnparsedDismissals = UnparsedDismissals.None,
) {
    private val mutex = Mutex()

    /** Bumped under [mutex] whenever the stored data is replaced wholesale. */
    @Volatile
    private var generation = 0L

    /** For scans: pass it to every [ingest] call so messages read before a data replace are dropped. */
    fun generation(): Long = generation

    /** [scrubTransactionBodies] already ran in this process; rows stay masked from then on. */
    @Volatile
    private var transactionBodiesScrubbed = false

    /**
     * Applies the current storage rules to the unparsed list: entries that are not transaction
     * alerts (OTPs, offers, reminders kept by v1.0.1 and older) are deleted, the rest are masked.
     * Idempotent and cheap (at most [UnparsedSmsDao.MAX_ROWS] rows), so it runs on every reconcile.
     */
    suspend fun scrubUnparsed() = withContext(Dispatchers.IO) {
        mutex.withLock {
            db.withTransaction {
                val dao = db.unparsedSmsDao()
                dao.getAll().forEach { row ->
                    val body = row.body ?: return@forEach
                    val sms = RawSms(row.sender, body, row.receivedAt)
                    val keep = when (row.reason) {
                        UnparsedReason.NO_MATCH, UnparsedReason.PARSER_ERROR ->
                            !parser.isKnownBankSender(row.sender) || parser.parse(sms) !is SmsParseResult.Ignored

                        UnparsedReason.UNKNOWN_SENDER -> parser.looksLikeUnknownBankAlert(sms)

                        else -> true
                    }
                    if (!keep) {
                        dao.delete(row.id)
                    } else {
                        val redacted = parser.redactUnparsed(body)
                        if (redacted != body) dao.updateBody(row.id, redacted)
                    }
                }
            }
        }
        scrubTransactionBodies()
    }

    /**
     * Masks the SMS bodies 1.0.3 and older stored unmasked on parsed transactions (ADR-0055;
     * new rows are masked in [store]). Idempotent, so once per process is only an optimization.
     */
    private suspend fun scrubTransactionBodies() {
        if (transactionBodiesScrubbed) return
        mutex.withLock {
            db.withTransaction {
                val dao = db.transactionDao()
                dao.storedBodies().forEach { row ->
                    val redacted = parser.redactUnparsed(row.smsBody)
                    if (redacted != row.smsBody) dao.updateBody(row.id, redacted)
                }
            }
        }
        transactionBodiesScrubbed = true
    }

    /**
     * Runs [block] with ingestion paused, then marks every scan that started earlier as stale.
     * Restore and "start fresh" use it, so a scan already iterating the inbox can't write old
     * messages into the new data (ADR-0027).
     */
    suspend fun <T> replacingData(block: suspend () -> T): T = mutex.withLock {
        generation++
        block()
    }

    /** [scanGeneration]: [generation] when the calling scan started; null for a single new SMS. */
    suspend fun ingest(sms: RawSms, origin: IngestOrigin, scanGeneration: Long? = null): IngestOutcome = withContext(
        Dispatchers.IO,
    ) {
        // Step 1: never parse messages from senders that aren't banks. One that still reads like a
        // bank alert is kept in the unparsed list, so a missing parser shows up (ADR-0028).
        val result = when {
            parser.isKnownBankSender(sms.sender) -> parser.parse(sms)
            parser.looksLikeUnknownBankAlert(sms) -> SmsParseResult.Unparsed(UnparsedReason.UNKNOWN_SENDER)
            else -> return@withContext IngestOutcome.NotFinancial
        }
        // An OTP, offer or reminder from a bank: nothing is stored (hard constraint 4).
        if (result is SmsParseResult.Ignored) return@withContext IngestOutcome.NotFinancial
        val autoSortEnabled = autoSort.enabled()
        val dismissedUpTo = if (result is SmsParseResult.Unparsed) dismissals.upTo() else null
        val dismissedHashes = if (result is SmsParseResult.Unparsed) dismissals.hashes() else emptySet()
        val outcome = mutex.withLock {
            if (scanGeneration != null && scanGeneration != generation) return@withLock IngestOutcome.Superseded
            db.withTransaction {
                when (result) {
                    is SmsParseResult.Transaction -> store(result.parsed, origin, autoSortEnabled)

                    is SmsParseResult.BalanceUpdate -> {
                        val accountId = accountFor(result.bankName, result.accountLast4, AccountKind.BANK)
                        db.accountDao().updateBalance(accountId, result.balancePaise)
                        IngestOutcome.BalanceUpdated
                    }

                    SmsParseResult.Ignored -> IngestOutcome.NotFinancial

                    is SmsParseResult.Unparsed -> {
                        // Deleted from the list by the user: a rescan must not bring it back.
                        val bodyHash = sha256(sms.body)
                        val dismissed = bodyHash in dismissedHashes ||
                            dismissedUpTo?.let { sms.receivedAt <= it } ?: false
                        if (!dismissed) {
                            db.unparsedSmsDao().insertCapped(
                                UnparsedSmsEntity(
                                    sender = sms.sender,
                                    bodyHash = bodyHash,
                                    // Only bank-like alerts reach this point (step 1), with
                                    // account, UPI and reference numbers masked.
                                    body = parser.redactUnparsed(sms.body),
                                    receivedAt = sms.receivedAt,
                                    reason = result.reason,
                                ),
                            )
                        }
                        IngestOutcome.Unparsed(result.reason)
                    }
                }
            }
        }
        // Step 7 (notification), after the commit so listeners see the stored row.
        if (outcome is IngestOutcome.Inserted && !outcome.autoSorted) events.pendingAdded(outcome.id, origin)
        outcome
    }

    private suspend fun store(parsed: ParsedSms, origin: IngestOrigin, autoSortEnabled: Boolean): IngestOutcome {
        val accountId =
            accountFor(parsed.bankName, parsed.accountLast4, if (parsed.isCard) AccountKind.CARD else AccountKind.BANK)
        val now = clock.nowMillis()
        val merchantKey = MerchantKeyNormalizer.normalize(parsed.merchant)
        // A name the user gave this merchant wins over the parser's text (SCOPE.md F7).
        val alias = merchantKey?.let { db.merchantAliasDao().displayName(it) }
        val id = db.transactionDao().insertOrIgnore(
            TransactionEntity(
                txnHash = parsed.txnHash,
                source = TransactionSource.SMS,
                amountPaise = parsed.amountPaise,
                direction = parsed.direction,
                parserType = parsed.parserType,
                merchantRaw = parsed.merchant,
                merchantDisplay = alias ?: MerchantKeyNormalizer.display(parsed.merchant),
                merchantKey = merchantKey,
                accountId = accountId,
                occurredAt = parsed.occurredAt,
                smsSender = parsed.sender,
                // Masked like the unparsed list: the account, UPI and reference numbers in the
                // body identify the user, and amount, date and wording are enough to check it.
                smsBody = parser.redactUnparsed(parsed.body),
                reference = parsed.reference,
                balanceAfterPaise = parsed.balanceAfterPaise,
                isCard = parsed.isCard,
                status = TransactionStatus.PENDING,
                isBackfill = origin == IngestOrigin.BACKFILL,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (id == -1L) return IngestOutcome.AlreadyStored

        parsed.balanceAfterPaise?.let { db.accountDao().updateBalance(accountId, it) }

        // Step 5: the same payment reported by another bank or app is linked, never merged.
        val window = DUPLICATE_WINDOW.inWholeMilliseconds
        val original = db.transactionDao().findCrossSourceDuplicate(
            excludeId = id,
            amountPaise = parsed.amountPaise,
            direction = parsed.direction,
            fromMillis = parsed.occurredAt - window,
            toMillis = parsed.occurredAt + window,
            bankName = parsed.bankName,
        )
        original?.let { db.transactionDao().setDuplicateOf(id, it.id) }

        // Step 6: transfers are not spending or income (ADR-0042). A message the parser marked as
        // a loan EMI, card bill or loan disbursal, and both legs of a transfer between two of the
        // user's accounts, go straight into the Transfers categories, which every total leaves out.
        val flow = linkTransferLegs(id, parsed, accountId, now) ?: parsed.flow
        val sortedAsTransfer = original == null && flow != null && sortIntoTransfers(id, flow, now)

        // Step 7: auto-sort well-known merchants, but never a possible duplicate (it needs a look).
        val autoSorted = sortedAsTransfer ||
            (autoSortEnabled && original == null && autoSortFromRule(id, merchantKey, now))
        return IngestOutcome.Inserted(id, original?.id, autoSorted)
    }

    /**
     * Links [id] with the other leg of the same money movement, on another of the user's accounts,
     * in the opposite direction, for the same amount, within [TRANSFER_WINDOW]. Only an
     * unambiguous single match is linked, and a waiting other leg is sorted too.
     * - Same bank reference: a transfer between own accounts.
     * - A loan EMI or card bill leg (parser hint) and a plain leg with the same amount: the bank
     *   debit and the loan account or card credit of one payment.
     * Returns the flow that now applies to [id], or null to keep the parser's.
     */
    private suspend fun linkTransferLegs(id: Long, parsed: ParsedSms, accountId: Long, now: Long): FlowHint? {
        val dao = db.transactionDao()
        val window = TRANSFER_WINDOW.inWholeMilliseconds
        val from = parsed.occurredAt - window
        val to = parsed.occurredAt + window
        val reference = parsed.reference?.takeIf { it.length >= MIN_REFERENCE_LENGTH }
        reference?.let {
            dao.findOtherTransferLeg(id, it, parsed.amountPaise, parsed.direction, accountId, from, to).singleOrNull()
        }?.let { other ->
            link(id, other, FlowHint.OWN_TRANSFER, now)
            return FlowHint.OWN_TRANSFER
        }
        val paired = parsed.flow?.takeIf { it in PAIRED_FLOWS }
        val candidates = dao.findOppositeLegs(id, parsed.amountPaise, parsed.direction, accountId, from, to)
        if (paired != null) {
            // This leg is the hinted one: pair it with the one other leg of the same amount.
            val other = candidates.singleOrNull() ?: return null
            link(id, other, paired, now)
            return paired
        }
        // A plain leg: is it the other half of a hinted loan or card payment already stored?
        val other = candidates.singleOrNull() ?: return null
        val otherFlow = other.categoryId?.let { db.categoryDao().get(it)?.seedKey }?.let(TransferSeeds::flowFor)
            ?.takeIf { it in PAIRED_FLOWS } ?: return null
        link(id, other, otherFlow, now)
        return otherFlow
    }

    private suspend fun link(id: Long, other: TransactionEntity, flow: FlowHint, now: Long) {
        db.transactionDao().setLinkedTo(id, other.id)
        db.transactionDao().setLinkedTo(other.id, id)
        if (other.status == TransactionStatus.PENDING) sortIntoTransfers(other.id, flow, now)
    }

    /** False when the user archived or turned that Transfers category into a group; it then waits in the inbox. */
    private suspend fun sortIntoTransfers(id: Long, flow: FlowHint, now: Long): Boolean {
        val category = db.categoryDao().bySeedKey(TransferSeeds.keyFor(flow)) ?: return false
        if (category.archived || db.categoryDao().childIds(category.id).isNotEmpty()) return false
        db.transactionDao().categorize(id, category.id, note = null, auto = true, now = now)
        return true
    }

    private suspend fun autoSortFromRule(id: Long, merchantKey: String?, now: Long): Boolean {
        val rule = merchantKey?.let { db.merchantRuleDao().get(it) } ?: return false
        if (rule.confirmCount < AUTO_SORT_MIN_CONFIRMATIONS) return false
        val category = db.categoryDao().get(rule.categoryId) ?: return false
        if (category.archived || db.categoryDao().childIds(category.id).isNotEmpty()) return false
        db.transactionDao().categorize(id, category.id, note = null, auto = true, now = now)
        return true
    }

    private suspend fun accountFor(bankName: String, last4: String?, kind: AccountKind): Long {
        val dao = db.accountDao()
        return dao.find(bankName, last4)?.id
            ?: dao.insert(AccountEntity(bankName = bankName, last4 = last4, kind = kind, createdAt = clock.nowMillis()))
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        /** SCOPE.md F2: same amount and direction within 10 minutes. */
        val DUPLICATE_WINDOW = 10.minutes

        /** SCOPE.md F8: "merchants I've confirmed 3+ times". */
        const val AUTO_SORT_MIN_CONFIRMATIONS = 3

        /** Both legs of an own-account transfer arrive within minutes; 3 days covers NEFT batches and late SMS. */
        val TRANSFER_WINDOW = 3.days

        /** Shorter "references" (a 4-digit transaction number) repeat too often to link on. */
        const val MIN_REFERENCE_LENGTH = 6

        /** Payments whose two sides (bank debit, loan or card credit) are linked by amount. */
        private val PAIRED_FLOWS = setOf(FlowHint.LOAN_REPAYMENT, FlowHint.CARD_BILL_PAYMENT)
    }
}
