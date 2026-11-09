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
