package io.github.swashbuckler104.budgetwidget.core.model

/**
 * Formats paise as rupees with Indian digit grouping, e.g. `12345678` -> `₹1,23,456.78`.
 * Whole-rupee amounts drop the decimals (`₹500`). Negative values get a leading `-`.
 */
fun formatInr(paise: Long): String {
    // Long.MIN_VALUE has no positive counterpart, so work on the unsigned magnitude.
    val negative = paise < 0
    val magnitude = if (negative) (-(paise + 1)).toULong() + 1u else paise.toULong()
    val rupees = (magnitude / 100u).toString()
    val fraction = (magnitude % 100u).toInt()
    val grouped = if (rupees.length <= 3) {
        rupees
    } else {
        val head = rupees.dropLast(3)
        head.reversed().chunked(2).joinToString(",").reversed() + "," + rupees.takeLast(3)
    }
    val decimals = if (fraction == 0) "" else ".%02d".format(fraction)
    return (if (negative) "-" else "") + "₹" + grouped + decimals
}

/**
 * Parses what someone typed as rupees into paise: `450`, `1,200.5`, `₹ 99.99`. Null for blank,
 * malformed, non-positive input or more than 2 decimals (no rounding: the user typed it).
 */
fun parseInrToPaise(text: String): Long? {
    val cleaned = text.filterNot { it == '₹' || it == ',' || it.isWhitespace() }
    if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1 || !cleaned.all { it.isDigit() || it == '.' }) return null
    val whole = cleaned.substringBefore('.').ifEmpty { "0" }
    val fraction = cleaned.substringAfter('.', "")
    if (fraction.length > 2 || whole.length > MAX_RUPEE_DIGITS) return null
    val paise = whole.toLong() * 100 + fraction.padEnd(2, '0').toLong()
    return paise.takeIf { it > 0 }
}

/** 15 digits of rupees is far beyond any real amount and keeps the paise inside a Long. */
private const val MAX_RUPEE_DIGITS = 15
