package io.github.swashbuckler104.budgetwidget.core.data

import java.util.Locale

/**
 * Turns a raw merchant string into the key merchant rules are stored under (SCOPE.md F8):
 * lowercase; UPI handles (`@ybl`), reference numbers, digit runs of 4 or more and punctuation
 * removed; whitespace collapsed. Returns null when nothing meaningful is left.
 */
object MerchantKeyNormalizer {
    private val upiHandle = Regex("@[\\p{L}\\p{N}._-]+")
    private val reference =
        Regex(
            "\\b(?:upi\\s+)?(?:ref|txn|utr|rrn)(?:\\s*(?:no|id|number))?[\\s.:#-]*[\\p{L}\\p{N}]*\\p{N}[\\p{L}\\p{N}]*",
        )
    private val longDigitRun = Regex("\\p{N}{4,}")
    private val punctuation = Regex("[^\\p{L}\\p{N}\\s]")
    private val whitespace = Regex("\\s+")

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val key = raw.lowercase(Locale.ROOT)
            .replace(upiHandle, " ")
            .replace(reference, " ")
            .replace(longDigitRun, " ")
            .replace(punctuation, " ")
            .replace(whitespace, " ")
            .trim()
        return key.ifEmpty { null }
    }

    /** What the user sees: the raw merchant with whitespace collapsed. */
    fun display(raw: String?): String? = raw?.replace(whitespace, " ")?.trim()?.ifEmpty { null }
}
