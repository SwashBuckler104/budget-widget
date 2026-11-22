package io.github.swashbuckler104.budgetwidget.core.data.sort

/**
 * What a sort also does for the merchant's other payments (ADR-0062), two separate choices:
 * [earlier] moves the payments already stored, [newer] sorts the ones still to come.
 */
data class MerchantScope(val earlier: Boolean = false, val newer: Boolean = false) {
    val any: Boolean get() = earlier || newer

    companion object {
        val NONE = MerchantScope()
        val BOTH = MerchantScope(earlier = true, newer = true)
    }
}
