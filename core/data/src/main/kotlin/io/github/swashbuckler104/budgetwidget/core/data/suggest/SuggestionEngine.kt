package io.github.swashbuckler104.budgetwidget.core.data.suggest

import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.group
import io.github.swashbuckler104.budgetwidget.core.data.category.toModel
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.accepts
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

/** What we know about a transaction when suggesting categories for it. Money is paise. */
data class SuggestionRequest(
    val merchantKey: String?,
    val direction: Direction,
    val amountPaise: Long,
    val parserType: ParserType?,
)

/**
 * Category suggestions for chips, notification actions and widgets (SCOPE.md F8), in order:
 * 1. the merchant's rule; 2. categories used for this merchant before; 3. the transaction type's
 * hint (transfer, investment; SCOPE.md F2 table); 4. most used in the last 60 days for the same
 * direction and an amount within ±50%; 5. most used overall for the direction; 6. seed defaults;
 * then any remaining leaf in display order. Only active leaves whose kind fits the direction.
 */
class SuggestionEngine @Inject constructor(private val db: BudgetDatabase, private val clock: WallClock) {
    suspend fun suggest(request: SuggestionRequest, limit: Int = DEFAULT_LIMIT): List<Category> {
        val entities = db.categoryDao().getAll()
        val bySeedKey = entities.filter { it.seedKey != null }.associate { it.seedKey!! to it.id }
        val leaves = group(entities.map { it.toModel() })
            .flatMap { it.pickableLeaves }
            .filter { it.kind.accepts(request.direction) }
        val tx = db.transactionDao()
        val key = request.merchantKey
        val amount = request.amountPaise
        val sources = listOf(
            listOfNotNull(key?.let { db.merchantRuleDao().get(it)?.categoryId }),
            key?.let { k -> tx.usageForMerchant(k).map { it.categoryId } }.orEmpty(),
            typeHintKeys(request.parserType).mapNotNull(bySeedKey::get),
            tx.usageInAmountBand(
                direction = request.direction,
                minPaise = amount / 2,
                maxPaise = saturatingAdd(amount, amount / 2),
                sinceMillis = clock.nowMillis() - RECENT_WINDOW.inWholeMilliseconds,
            ).map { it.categoryId },
            tx.usageForDirection(request.direction).map { it.categoryId },
            defaultKeys(request.direction).mapNotNull(bySeedKey::get),
        )
        val byId = leaves.associateBy { it.id }
        return SuggestionRanker.rank(sources, leaves.map { it.id }, limit).map(byId::getValue)
    }

    private fun typeHintKeys(type: ParserType?): List<String> = when (type) {
        // Kind filtering picks the right leg: Financial Expenses for OUT, Income for IN.
        ParserType.TRANSFER -> listOf("self_transfer_out", "self_transfer", "savings_ef")

        ParserType.INVESTMENT -> listOf("financial_investments", "savings_ef")

        else -> emptyList()
    }

    private fun defaultKeys(direction: Direction): List<String> = when (direction) {
        Direction.OUT -> listOf("groceries", "restaurants", "fuel", "public_transport", "drugstore", "phone")
        Direction.IN -> listOf("salary", "freelancing", "self_transfer", "other_income")
    }

    private fun saturatingAdd(a: Long, b: Long): Long = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    companion object {
        /** Widget chips (SCOPE.md F4); notifications take the first 3. */
        const val DEFAULT_LIMIT = 6
        val RECENT_WINDOW = 60.days
    }
}

internal object SuggestionRanker {
    /**
     * Concatenates [sources] in priority order, keeps only [eligible] ids, removes repeats, and
     * fills up from [eligible] (display order) until [limit].
     */
    fun rank(sources: List<List<Long>>, eligible: List<Long>, limit: Int): List<Long> {
        val allowed = eligible.toSet()
        val result = LinkedHashSet<Long>()
        for (id in sources.asSequence().flatten() + eligible.asSequence()) {
            if (result.size >= limit) break
            if (id in allowed) result += id
        }
        return result.toList()
    }
}
