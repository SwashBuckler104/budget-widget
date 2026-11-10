package io.github.swashbuckler104.budgetwidget.core.data.edit

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.data.MerchantKeyNormalizer
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortResult
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantAliasEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.UnparsedSmsEntity
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.accepts
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Puts transactions (and the merchant rules an action taught) back as they were (SCOPE.md F7
 * "Undo snackbar for every destructive action"). Opaque outside this package.
 */
class Undo internal constructor(
    /** The rows as they were before the action. */
    internal val rows: List<TransactionEntity>,
    /**
     * Each row's `updatedAt` right after the action (null: the action deleted it). A row that has
     * changed since (edited, sorted elsewhere) is left alone: undo never overwrites a later edit.
     */
    internal val stamps: Map<Long, Long?>,
    internal val rules: Map<String, MerchantRuleEntity?> = emptyMap(),
    /** Merchant aliases as they were (null: there was none). */
    internal val aliases: Map<String, String?> = emptyMap(),
)

/**
 * How many transactions an action changed, how many it had to skip (for example a category that
 * doesn't fit an income), and how to take it back (null when nothing changed).
 */
data class Undoable(val changed: Int, val undo: Undo?, val skipped: Int = 0)

/** Everything the edit sheet can change (SCOPE.md F7). */
data class TransactionEdit(
    val id: Long,
    val direction: Direction,
    /** The leaf to sort into; null keeps the current category (or none, for an unsorted one). */
    val categoryId: Long?,
    val note: String?,
    val merchantName: String?,
    /** Rename every transaction from this merchant, and new ones from now on. */
    val applyNameToMerchant: Boolean = false,
    /** Teach the merchant rule when the category changes (SCOPE.md F8). */
    val rememberMerchant: Boolean = true,
    /** Manual transactions only: SMS amounts and times are read-only. */
    val amountPaise: Long? = null,
    val occurredAt: Long? = null,
)

/** A cash (manual) transaction (SCOPE.md F7 "Add manual transaction"). [occurredAt] is epoch ms. */
data class ManualTransaction(
    val amountPaise: Long,
    val direction: Direction,
    val categoryId: Long,
    val merchantName: String?,
    val note: String?,
    val occurredAt: Long,
)

sealed interface EditResult {
    /** [renamed] other transactions took the new merchant name; [undo] takes that back. */
    data class Saved(val id: Long, val renamed: Int = 0, val undo: Undo? = null) : EditResult

    data object NotFound : EditResult

    /** Zero, negative or above [TransactionEditor.MAX_AMOUNT_PAISE]. */
    data object InvalidAmount : EditResult

    /** The category's kind can't hold money moving this way (an income category for a payment). */
    data object CategoryMismatch : EditResult

    data class SortFailed(val result: SortResult) : EditResult
}

/**
 * Edits from the Transactions tab. Sorting itself still goes through [SortTransactionUseCase], so
 * notifications and widgets follow every change the same way.
 */
class TransactionEditor @Inject constructor(
    private val db: BudgetDatabase,
    private val sort: SortTransactionUseCase,
    private val events: InboxEvents,
    private val clock: WallClock,
) {
    private val dao get() = db.transactionDao()

    /**
     * Saves the edit sheet in one transaction: the details and the sort land together or not at
     * all, so nobody observes (say) incoming money in an expense category. The "sorted" event is
     * reported after the commit, like [SortTransactionUseCase] does.
     */
    suspend fun save(edit: TransactionEdit): EditResult {
        val outcome = try {
            db.withTransaction {
                val rename = saveDetails(edit)
                val sorted = sortInSave(edit)
                rename to sorted
            }
        } catch (e: EditAborted) {
            return e.result
        }
        val (rename, sorted) = outcome
        if (sorted) events.sorted(edit.id)
        return EditResult.Saved(edit.id, rename?.others ?: 0, rename?.let { renameUndo(edit.id, it) })
    }

    /** Sorts as part of [save]; true when it changed the category (or confirmed an auto-sort). */
    private suspend fun sortInSave(edit: TransactionEdit): Boolean {
        val categoryId = edit.categoryId ?: return false
        val current = dao.get(edit.id) ?: throw EditAborted(EditResult.NotFound)
        // The same category again only matters for an auto-sorted one: saving confirms it
        // (it leaves "Review auto-sorted", SCOPE.md F8).
        if (current.status == TransactionStatus.CATEGORIZED && current.categoryId == categoryId &&
            !current.autoCategorized
        ) {
            return false
        }
        val result = sort.categorizeLocked(
            edit.id,
            categoryId,
            current.note,
            edit.rememberMerchant,
            requirePending = false,
        )
        if (result != SortResult.Done) throw EditAborted(EditResult.SortFailed(result))
        return true
    }

    /** Rolls a [save] back: thrown inside the transaction, carries what to report. */
    private class EditAborted(val result: EditResult) : RuntimeException()

    /** What "rename every transaction from this merchant" overwrote, for its undo. */
    private class Rename(
        val key: String,
        val oldName: String?,
        val others: Int,
        val before: List<TransactionEntity>,
        val alias: String?,
    )

    /**
     * Undo for a merchant rename: the other transactions and the alias go back, and the edited one
     * gets its old name back (its other edits stay).
     */
    private suspend fun renameUndo(id: Long, rename: Rename): Undo? {
        val edited = dao.get(id) ?: return null
        val rows = rename.before + edited.copy(merchantDisplay = rename.oldName)
        val after = dao.getMany(rows.map { it.id }).associate { it.id to it.updatedAt }
        return Undo(rows, rows.associate { it.id to after[it.id] }, aliases = mapOf(rename.key to rename.alias))
    }

    private suspend fun saveDetails(edit: TransactionEdit): Rename? {
        val tx = dao.get(edit.id) ?: throw EditAborted(EditResult.NotFound)
        val manual = tx.source == TransactionSource.MANUAL
        val amount = if (manual) edit.amountPaise ?: tx.amountPaise else tx.amountPaise
        if (amount !in 1..MAX_AMOUNT_PAISE) throw EditAborted(EditResult.InvalidAmount)
        val categoryId = edit.categoryId ?: tx.categoryId
        if (categoryId != null) {
            val kind = db.categoryDao().get(categoryId)?.kind
            if (kind != null && !kind.accepts(edit.direction)) throw EditAborted(EditResult.CategoryMismatch)
        }
        val name = MerchantKeyNormalizer.display(edit.merchantName)
        val renamed = name != tx.merchantDisplay
        // Naming an unknown merchant gives it a key, so its rule and suggestions can learn.
        val key = tx.merchantKey ?: if (renamed) MerchantKeyNormalizer.normalize(name) else null
        val now = clock.nowMillis()
        dao.update(
            listOf(
                tx.copy(
                    direction = edit.direction,
                    merchantDisplay = name,
                    merchantKey = key,
                    note = cleanNote(edit.note),
                    amountPaise = amount,
                    occurredAt = if (manual) edit.occurredAt ?: tx.occurredAt else tx.occurredAt,
                    editedByUser = true,
                    updatedAt = now,
                ),
            ),
        )
        if (!(renamed && edit.applyNameToMerchant && key != null && name != null)) return null
        val others = dao.byMerchantKey(key).filter { it.id != edit.id }
        val alias = db.merchantAliasDao().displayName(key)
        dao.renameMerchant(key, name, now)
        db.merchantAliasDao().upsert(MerchantAliasEntity(key, name))
        return Rename(key, tx.merchantDisplay, others.size, others, alias)
    }

    /** Stores a cash transaction, already sorted (it never waits in the inbox). */
    suspend fun addManual(manual: ManualTransaction): EditResult = db.withTransaction {
        if (manual.amountPaise !in 1..MAX_AMOUNT_PAISE) return@withTransaction EditResult.InvalidAmount
        val category = db.categoryDao().get(manual.categoryId)
            ?: return@withTransaction EditResult.SortFailed(SortResult.CategoryNotFound)
        when {
            category.archived -> return@withTransaction EditResult.SortFailed(SortResult.CategoryArchived)

            db.categoryDao().childIds(category.id).isNotEmpty() ->
                return@withTransaction EditResult.SortFailed(SortResult.NotALeaf)

            !category.kind.accepts(manual.direction) -> return@withTransaction EditResult.CategoryMismatch
        }
        val now = clock.nowMillis()
        val id = dao.insert(
            TransactionEntity(
                txnHash = null,
                source = TransactionSource.MANUAL,
                amountPaise = manual.amountPaise,
                direction = manual.direction,
                parserType = null,
                merchantRaw = null,
                merchantDisplay = MerchantKeyNormalizer.display(manual.merchantName),
                merchantKey = MerchantKeyNormalizer.normalize(manual.merchantName),
                accountId = null,
                occurredAt = manual.occurredAt,
                smsSender = null,
                smsBody = null,
                reference = null,
                balanceAfterPaise = null,
                isCard = false,
                status = TransactionStatus.CATEGORIZED,
                categoryId = category.id,
                isBackfill = false,
                note = cleanNote(manual.note),
                editedByUser = true,
                createdAt = now,
                updatedAt = now,
                sortedAt = now,
            ),
        )
        EditResult.Saved(id)
    }

    /**
     * Sorts [ids] into [categoryId]. Bulk sorts pass `rememberMerchant = false`: one choice for a
     * mixed selection shouldn't become every merchant's rule.
     */
    suspend fun categorize(ids: Collection<Long>, categoryId: Long, rememberMerchant: Boolean): Undoable {
        var skipped = 0
        val done = undoable(ids, withRules = rememberMerchant) { id ->
            val result = sort.categorize(id, categoryId, dao.get(id)?.note, rememberMerchant)
            // A mixed selection (payments and income) can't all fit one category: skip the rest.
            if (result == SortResult.KindMismatch) skipped++
            result == SortResult.Done
        }
        return done.copy(skipped = skipped)
    }

    suspend fun ignore(ids: Collection<Long>): Undoable = undoable(ids) { id -> sort.ignore(id) == SortResult.Done }

    /** "Merge" on a possible duplicate (SCOPE.md F2): ignored with reason DUPLICATE, never counted. */
    suspend fun merge(id: Long): Undoable =
        undoable(listOf(id)) { sort.ignore(it, IgnoreReason.DUPLICATE) == SortResult.Done }

    /** "Keep both": no longer flagged as a duplicate. */
    suspend fun keepBoth(id: Long): Undoable = undoable(listOf(id)) { dao.clearDuplicate(it, clock.nowMillis()) > 0 }

    /** Back to the inbox. */
    suspend fun unignore(id: Long): Undoable = undoable(listOf(id)) { dao.unignore(it, clock.nowMillis()) > 0 }

    /** Manual transactions only; SMS ones are ignored instead. */
    suspend fun deleteManual(id: Long): Undoable = undoable(listOf(id)) { dao.deleteManual(it) > 0 }

    /**
     * "Report wrong amount" (SCOPE.md F7): files the original SMS in the unparsed list, where the
     * user can copy it into a parser bug report. False when there is no SMS to report.
     */
    suspend fun reportWrongAmount(id: Long): Boolean {
        val tx = dao.get(id) ?: return false
        val body = tx.smsBody ?: return false
        db.unparsedSmsDao().insertCapped(
            UnparsedSmsEntity(
                sender = tx.smsSender.orEmpty(),
                bodyHash = sha256(body),
                body = body,
                receivedAt = tx.occurredAt,
                reason = UnparsedReason.WRONG_AMOUNT,
            ),
        )
        return true
    }

    /**
     * Restores what [undo] captured, skipping rows changed since the action. Deleted rows come
     * back with their ids. Returns how many rows it restored.
     *
     * No inbox events: a transaction put back in the inbox gets no new notification (the user is
     * in the app looking at it); widgets follow the database on their own.
     */
    suspend fun undo(undo: Undo): Int = db.withTransaction {
        val current = dao.getMany(undo.rows.map { it.id }).associateBy { it.id }
        val restore = undo.rows.filter { row ->
            val stamp = undo.stamps[row.id]
            val now = current[row.id]
            if (stamp == null) now == null else now?.updatedAt == stamp
        }
        val (present, deleted) = restore.partition { it.id in current }
        dao.update(present)
        deleted.forEach { dao.insert(it) }
        val rules = db.merchantRuleDao()
        undo.rules.forEach { (key, rule) -> if (rule == null) rules.delete(key) else rules.upsert(rule) }
        val aliases = db.merchantAliasDao()
        undo.aliases.forEach { (key, name) ->
            if (name ==
                null
            ) {
                aliases.delete(key)
            } else {
                aliases.upsert(MerchantAliasEntity(key, name))
            }
        }
        restore.size
    }

    private suspend fun undoable(
        ids: Collection<Long>,
        withRules: Boolean = false,
        action: suspend (Long) -> Boolean,
    ): Undoable {
        val before = dao.getMany(ids.distinct())
        val rules = if (withRules) {
            before.mapNotNull { it.merchantKey }.distinct().associateWith { db.merchantRuleDao().get(it) }
        } else {
            emptyMap()
        }
        val changed = before.filter { action(it.id) }
        if (changed.isEmpty()) return Undoable(0, null)
        val after = dao.getMany(changed.map { it.id }).associate { it.id to it.updatedAt }
        return Undoable(changed.size, Undo(changed, changed.associate { it.id to after[it.id] }, rules))
    }

    private fun cleanNote(note: String?): String? = note?.trim()?.take(SortTransactionUseCase.MAX_NOTE)?.ifEmpty {
        null
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        /** ₹1,00,00,00,000: anything larger is a typo. */
        const val MAX_AMOUNT_PAISE = 100_00_00_00_000L
    }
}
