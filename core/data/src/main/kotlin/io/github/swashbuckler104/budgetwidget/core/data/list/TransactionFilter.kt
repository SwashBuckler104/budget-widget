package io.github.swashbuckler104.budgetwidget.core.data.list

import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus

/** The status filter chip (SCOPE.md F7), plus "Review auto-sorted" (SCOPE.md F8). */
enum class StatusFilter { PENDING, CATEGORIZED, AUTO_SORTED, IGNORED }

/**
 * Search and filter chips of the Transactions tab (SCOPE.md F7). Every field narrows the list;
 * the defaults match everything. Times are epoch ms (UTC), [toMillis] exclusive.
 */
data class TransactionFilter(
    val query: String = "",
    /** A leaf, or a top-level category (then its subcategories match too). */
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val direction: Direction? = null,
    val status: StatusFilter? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    /** One merchant exactly (Analysis "Top merchants" drill-down). */
    val merchantKey: String? = null,
    /** Leave ignored ones out, so a drill-down list adds up to the total it came from. */
    val hideIgnored: Boolean = false,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || categoryId != null || accountId != null || direction != null ||
            status != null || fromMillis != null || toMillis != null || merchantKey != null || hideIgnored

    /**
     * [parentOf] gives a category's parent id (null for a top-level one), [categoryName] its name;
     * both return null for unknown ids.
     */
    fun matches(t: Transaction, parentOf: (Long) -> Long?, categoryName: (Long) -> String?): Boolean {
        if (direction != null && t.direction != direction) return false
        if (accountId != null && t.accountId != accountId) return false
        if (fromMillis != null && t.occurredAt < fromMillis) return false
        if (toMillis != null && t.occurredAt >= toMillis) return false
        if (status != null && !status.matches(t)) return false
        if (merchantKey != null && t.merchantKey != merchantKey) return false
        if (hideIgnored && t.status == TransactionStatus.IGNORED) return false
        if (categoryId != null) {
            val leaf = t.categoryId ?: return false
            if (leaf != categoryId && parentOf(leaf) != categoryId) return false
        }
        return query.isBlank() || matchesQuery(t, query.trim(), categoryName)
    }

    private fun StatusFilter.matches(t: Transaction): Boolean = when (this) {
        StatusFilter.PENDING -> t.status == TransactionStatus.PENDING
        StatusFilter.CATEGORIZED -> t.status == TransactionStatus.CATEGORIZED
        StatusFilter.AUTO_SORTED -> t.status == TransactionStatus.CATEGORIZED && t.autoCategorized
        StatusFilter.IGNORED -> t.status == TransactionStatus.IGNORED
    }

    companion object {
        /**
         * Merchant, note, category or bank name containing [query] (ignoring case), or an amount
         * starting with it: "45" finds ₹450 and ₹4,500; "₹1,200.5" finds ₹1,200.50.
         */
        internal fun matchesQuery(t: Transaction, query: String, categoryName: (Long) -> String?): Boolean {
            val texts = listOfNotNull(t.merchantDisplay, t.note, t.bankName, t.categoryId?.let(categoryName))
            if (texts.any { it.contains(query, ignoreCase = true) }) return true
            val digits = query.filterNot { it == '₹' || it == ',' || it.isWhitespace() }
            if (digits.isEmpty() || !digits.all { it.isDigit() || it == '.' } || digits.count { it == '.' } > 1) {
                return false
            }
            return plainAmount(t.amountPaise).startsWith(digits)
        }

        /** `45000` paise -> `450`, `120050` -> `1200.50`: how people type amounts. */
        internal fun plainAmount(paise: Long): String {
            val rupees = paise / 100
            val fraction = paise % 100
            return if (fraction == 0L) rupees.toString() else "$rupees.%02d".format(fraction)
        }
    }
}
